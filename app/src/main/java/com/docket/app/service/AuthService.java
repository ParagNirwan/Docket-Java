package com.docket.app.service;

import com.docket.app.dto.AuthResponseDTO;
import com.docket.app.dto.LoginRequestDTO;
import com.docket.app.dto.RegisterRequestDTO;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final int LOGIN_LIMIT_PER_IP = 20;
    private static final int LOGIN_LIMIT_PER_ACCOUNT = 5;
    private static final int LOCKOUT_FAILURES = 10;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AccountRecoveryService accountRecoveryService;
    private final InvitationService invitationService;
    private final AuditService auditService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientRequestInfo clientRequestInfo;

    // Compared against when an email isn't registered. See login().
    private volatile String dummyPasswordHash;

    // The new user signs in separately, so this token has no session behind it.
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists.");
        }

        User user = new User(
                request.getFirstName().trim() + " " + request.getLastName().trim(),
                email,
                passwordEncoder.encode(request.getPassword())
        );

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists.", exception);
        }

        // The account exists by now, and the email can be re-sent from the app, so
        // a problem here shouldn't turn a successful registration into an error.
        try {
            if (!acceptedInvite(request.getInviteToken(), user)) {
                accountRecoveryService.sendVerificationEmail(user);
            }
        } catch (RuntimeException exception) {
            log.error("Could not queue verification email for user {}", user.getId(), exception);
        }

        return new AuthResponseDTO(jwtService.generateAccessToken(user, null));
    }

    // Accepting proves the address, so there's nothing left to verify. An invite that has
    // expired in the meantime just means this person registers without joining anything.
    private boolean acceptedInvite(String inviteToken, User user) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return false;
        }
        try {
            return invitationService.acceptForNewUser(inviteToken, user);
        } catch (RuntimeException exception) {
            log.warn("Could not accept invite while registering user {}", user.getId(), exception);
            return false;
        }
    }

    public SessionService.IssuedSession login(LoginRequestDTO loginRequestDTO) {
        String email = normalizeEmail(loginRequestDTO.getEmail());
        String lockoutKey = lockoutKey(email);

        authRateLimiter.check("login:ip:" + clientRequestInfo.ip(), LOGIN_LIMIT_PER_IP, ONE_MINUTE);
        authRateLimiter.check("login:account:" + email, LOGIN_LIMIT_PER_ACCOUNT, ONE_MINUTE);
        authRateLimiter.requireBelow(lockoutKey, LOCKOUT_FAILURES, LOCKOUT_WINDOW);

        Optional<User> found = userRepository.findByEmailIgnoreCase(email);

        // Checking a password takes around 100ms. Skipping that for unknown emails would
        // let an attacker discover who has an account just by timing the response.
        String passwordHash = found.map(User::getPasswordHash).orElseGet(this::dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(loginRequestDTO.getPassword(), passwordHash);

        if (found.isEmpty() || !passwordMatches) {
            recordFailedLogin(found, email, lockoutKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = found.get();
        authRateLimiter.reset(lockoutKey);
        SessionService.IssuedSession session = sessionService.start(user);
        auditService.recordAccountEvent(user.getId(), AuditAction.LOGIN, "USER", user.getId(), null);
        return session;
    }

    // Failures count against the email whether or not it's registered, so being locked
    // out doesn't reveal that an account exists either.
    private void recordFailedLogin(Optional<User> user, String email, String lockoutKey) {
        var actorId = user.map(User::getId).orElse(null);
        auditService.recordAccountEvent(actorId, AuditAction.LOGIN_FAILED, "USER", actorId, Map.of("email", email));
        if (authRateLimiter.increment(lockoutKey, LOCKOUT_WINDOW) == LOCKOUT_FAILURES) {
            auditService.recordAccountEvent(actorId, AuditAction.ACCOUNT_LOCKED, "USER", actorId, Map.of("email", email));
        }
    }

    static String lockoutKey(String email) {
        return "login:failures:" + email;
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode(TokenHasher.newToken());
        }
        return dummyPasswordHash;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
