package com.docket.app.service;

import com.docket.app.entity.RefreshToken;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.RefreshTokenRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
    private static final String RAW_TOKEN = "raw-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @Mock
    private ClientRequestInfo clientRequestInfo;

    @InjectMocks
    private SessionService sessionService;

    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final User user = new User("Ada", "ada@example.com", "hash");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionService, "refreshTtlDays", 14);
        ReflectionTestUtils.setField(sessionService, "reuseGraceSeconds", 10);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(jwtService.generateAccessToken(eq(user), any())).thenReturn("access");
    }

    private RefreshToken storedToken() {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void refreshTokensAreOnlyStoredHashed() {
        SessionService.IssuedSession session = sessionService.start(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertNotEquals(session.refreshToken(), saved.getValue().getTokenHash());
        assertEquals(TokenHasher.hash(session.refreshToken()), saved.getValue().getTokenHash());
    }

    @Test
    void rotationRetiresTheTokenAndContinuesTheSameFamily() {
        RefreshToken token = storedToken();

        Optional<SessionService.IssuedSession> result = sessionService.rotate(RAW_TOKEN);

        assertEquals("access", result.orElseThrow().accessToken());
        assertNotNull(token.getRotatedAt());
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertEquals(familyId, saved.getValue().getFamilyId());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void replayAfterTheGraceWindowRevokesTheFamilyWithoutThrowing() {
        // Returning empty rather than throwing is what lets the revocation commit.
        RefreshToken token = storedToken();
        token.setRotatedAt(Instant.now().minus(Duration.ofMinutes(1)));

        assertTrue(sessionService.rotate(RAW_TOKEN).isEmpty());

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any());
        verify(auditService).recordAccountEvent(userId, AuditAction.REFRESH_TOKEN_REUSED, "SESSION", familyId, null);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void replayInsideTheGraceWindowIsAConcurrentRefreshNotAnAttack() {
        RefreshToken token = storedToken();
        token.setRotatedAt(Instant.now().minus(Duration.ofSeconds(2)));

        assertTrue(sessionService.rotate(RAW_TOKEN).isPresent());

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void revokedAndExpiredTokensAreRefused() {
        RefreshToken token = storedToken();
        token.setRevokedAt(Instant.now());
        assertTrue(sessionService.rotate(RAW_TOKEN).isEmpty());

        token.setRevokedAt(null);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(sessionService.rotate(RAW_TOKEN).isEmpty());

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownTokenIsRefused() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertTrue(sessionService.rotate("nope").isEmpty());
    }

    @Test
    void revokeAllEndsRefreshTokensAndCurrentAccessTokens() {
        sessionService.revokeAllSessions(userId);

        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
        verify(userRepository).incrementTokenVersion(userId);
    }

    @Test
    void invalidatingAccessTokensLeavesTheSessionLoggedIn() {
        sessionService.invalidateAccessTokens(userId);

        verify(userRepository).incrementTokenVersion(userId);
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }
}
