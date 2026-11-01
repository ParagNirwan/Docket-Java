package com.docket.app.service;

import com.docket.app.entity.User;
import com.docket.app.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService();
    private final User user = new User("Ada", "ada@example.com", "hash");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlMs", 60_000L);
    }

    private AuthenticatedUser principal(int tokenVersion) {
        return new AuthenticatedUser("ada@example.com", "hash", List.of(), tokenVersion);
    }

    @Test
    void tokenIsValidWhileItsVersionMatches() {
        user.setTokenVersion(3);

        assertTrue(jwtService.isTokenValid(jwtService.generateAccessToken(user, null), principal(3)));
    }

    @Test
    void bumpingTheVersionInvalidatesExistingTokens() {
        user.setTokenVersion(3);
        String token = jwtService.generateAccessToken(user, null);

        assertFalse(jwtService.isTokenValid(token, principal(4)));
    }

    @Test
    void tokenForSomeoneElseIsInvalid() {
        String token = jwtService.generateAccessToken(user, null);

        assertFalse(jwtService.isTokenValid(token, new AuthenticatedUser("grace@example.com", "hash", List.of(), 0)));
    }

    @Test
    void sessionIdRoundTrips() {
        UUID sessionId = UUID.randomUUID();

        assertEquals(sessionId, jwtService.extractSessionId(jwtService.generateAccessToken(user, sessionId)));
        assertNull(jwtService.extractSessionId(jwtService.generateAccessToken(user, null)));
    }
}
