package com.docket.app.service;

import com.docket.app.entity.AccountToken;
import com.docket.app.entity.User;
import com.docket.app.enums.AccountTokenPurpose;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.AccountTokenRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

// Password reset and email verification: both prove someone controls an inbox by
// sending a single-use link to it. Only the token's hash is stored.
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final Duration VERIFICATION_TTL = Duration.ofHours(48);

    private final AccountTokenRepository accountTokenRepository;
    private final UserRepository userRepository;
    private final EmailOutboxService emailOutboxService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final AuthRateLimiter authRateLimiter;
    private final PasswordEncoder passwordEncoder;

    @Value("${docket.app.base-url}")
    private String baseUrl;

    // Does nothing visible for an unknown email, so this can't reveal who has an account.
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(user -> {
            String link = issueLink(user, AccountTokenPurpose.PASSWORD_RESET, RESET_TTL, "/reset-password");
            emailOutboxService.enqueue(user.getEmail(), EmailTemplate.PASSWORD_RESET, Map.of(
                    "name", user.getName(),
                    "link", link,
                    "expiryMinutes", RESET_TTL.toMinutes()));
            auditService.recordAccountEvent(user.getId(), AuditAction.PASSWORD_RESET_REQUESTED, "USER", user.getId(), null);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = consume(rawToken, AccountTokenPurpose.PASSWORD_RESET);
        Instant now = Instant.now();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // The reset link arrived in their inbox, so the address is proven too.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        userRepository.save(user);

        accountTokenRepository.consumeOutstanding(user.getId(), AccountTokenPurpose.PASSWORD_RESET, now);
        sessionService.revokeAllSessions(user.getId());
        authRateLimiter.reset(AuthService.lockoutKey(user.getEmail()));
        auditService.recordAccountEvent(user.getId(), AuditAction.PASSWORD_RESET, "USER", user.getId(), null);
    }

    @Transactional
    public void sendVerificationEmail(User user) {
        String link = issueLink(user, AccountTokenPurpose.EMAIL_VERIFICATION, VERIFICATION_TTL, "/verify-email");
        emailOutboxService.enqueue(user.getEmail(), EmailTemplate.EMAIL_VERIFICATION, Map.of(
                "name", user.getName(),
                "link", link,
                "expiryHours", VERIFICATION_TTL.toHours()));
    }

    // Does nothing if the address is already verified.
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getEmailVerifiedAt() == null) {
            sendVerificationEmail(user);
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = consume(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
            auditService.recordAccountEvent(user.getId(), AuditAction.EMAIL_VERIFIED, "USER", user.getId(), null);
        }
    }

    // Replaces any earlier link, so only the newest email works.
    private String issueLink(User user, AccountTokenPurpose purpose, Duration ttl, String path) {
        Instant now = Instant.now();
        accountTokenRepository.consumeOutstanding(user.getId(), purpose, now);

        String rawToken = TokenHasher.newToken();
        AccountToken token = new AccountToken();
        token.setUserId(user.getId());
        token.setPurpose(purpose);
        token.setTokenHash(TokenHasher.hash(rawToken));
        token.setExpiresAt(now.plus(ttl));
        token.setCreatedAt(now);
        accountTokenRepository.save(token);

        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    private User consume(String rawToken, AccountTokenPurpose purpose) {
        AccountToken token = accountTokenRepository.findByTokenHashAndPurpose(TokenHasher.hash(rawToken), purpose)
                .filter(found -> found.getConsumedAt() == null && found.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired"));
        token.setConsumedAt(Instant.now());
        return userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
