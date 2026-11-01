package com.docket.app.service;

import com.docket.app.dto.SessionDTO;
import com.docket.app.entity.RefreshToken;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.RefreshTokenRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Manages logged-in sessions. A session is a chain ("family") of refresh tokens: each
// refresh swaps the current token for a new one, so a stolen token that gets used
// shows up as a token being spent twice, and the whole session is ended.
@Service
@RequiredArgsConstructor
public class SessionService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final ClientRequestInfo clientRequestInfo;

    @Value("${docket.auth.refresh-ttl-days}")
    private int refreshTtlDays;

    @Value("${docket.auth.refresh-reuse-grace-seconds}")
    private int reuseGraceSeconds;

    @Transactional
    public IssuedSession start(User user) {
        return issue(user, UUID.randomUUID());
    }

    // Swaps a refresh token for a new pair. Empty means the token is no good.
    // This returns rather than throws on purpose: an exception would roll back the
    // transaction, and with it the revocation of a session whose token was replayed.
    @Transactional
    public Optional<IssuedSession> rotate(String rawRefreshToken) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = found.get();
        Instant now = Instant.now();
        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(now)) {
            return Optional.empty();
        }

        if (token.getRotatedAt() != null && !isWithinReuseGrace(token, now)) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            auditService.recordAccountEvent(token.getUserId(), AuditAction.REFRESH_TOKEN_REUSED, "SESSION", token.getFamilyId(), null);
            return Optional.empty();
        }

        if (token.getRotatedAt() == null) {
            token.setRotatedAt(now);
        }
        return userRepository.findById(token.getUserId()).map(user -> issue(user, token.getFamilyId()));
    }

    // Two browser tabs can legitimately present the same token a moment apart, or a
    // response can get lost on a bad connection. Ending the session for that would
    // log people out at random, so a very recent replay just gets another new token.
    private boolean isWithinReuseGrace(RefreshToken token, Instant now) {
        return token.getRotatedAt().plus(Duration.ofSeconds(reuseGraceSeconds)).isAfter(now);
    }

    // Logging out with an unknown or expired token is a no-op: the goal is already met.
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken)).ifPresent(token -> {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            auditService.recordAccountEvent(token.getUserId(), AuditAction.LOGOUT, "SESSION", token.getFamilyId(), null);
        });
    }

    public List<SessionDTO> listSessions(String email, UUID currentSessionId) {
        User user = findUser(email);
        // A family can briefly hold two live tokens after a grace-window refresh.
        Set<UUID> seen = new HashSet<>();
        return refreshTokenRepository.findActiveByUserId(user.getId(), Instant.now()).stream()
                .filter(token -> seen.add(token.getFamilyId()))
                .map(token -> new SessionDTO(
                        token.getFamilyId(),
                        token.getUserAgent(),
                        token.getIp(),
                        token.getCreatedAt(),
                        token.getFamilyId().equals(currentSessionId)))
                .toList();
    }

    // The session's current access token keeps working until it expires, at most
    // 15 minutes later, but it can't be refreshed again.
    @Transactional
    public void revokeSession(String email, UUID sessionId) {
        User user = findUser(email);
        if (refreshTokenRepository.revokeFamilyForUser(user.getId(), sessionId, Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        auditService.recordAccountEvent(user.getId(), AuditAction.SESSION_REVOKED, "SESSION", sessionId, null);
    }

    @Transactional
    public void revokeAllSessions(String email) {
        User user = findUser(email);
        revokeAllSessions(user.getId());
        auditService.recordAccountEvent(user.getId(), AuditAction.SESSION_REVOKED, "USER", user.getId(), Map.of("scope", "all"));
    }

    // Logs the user out everywhere, immediately. For when their credentials can't be
    // trusted any more, such as a password reset.
    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        userRepository.incrementTokenVersion(userId);
    }

    // Rejects the user's current access tokens but keeps them logged in: their next
    // request refreshes and picks up the change. For role changes and member removal,
    // which affect one organization and not the whole account.
    @Transactional
    public void invalidateAccessTokens(UUID userId) {
        userRepository.incrementTokenVersion(userId);
    }

    private IssuedSession issue(User user, UUID familyId) {
        String rawToken = TokenHasher.newToken();
        Instant now = Instant.now();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(TokenHasher.hash(rawToken));
        token.setExpiresAt(now.plus(refreshTtl()));
        token.setUserAgent(clientRequestInfo.userAgent());
        token.setIp(clientRequestInfo.ip());
        token.setCreatedAt(now);
        refreshTokenRepository.save(token);

        return new IssuedSession(jwtService.generateAccessToken(user, familyId), rawToken);
    }

    public Duration refreshTtl() {
        return Duration.ofDays(refreshTtlDays);
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public record IssuedSession(String accessToken, String refreshToken) {
    }
}
