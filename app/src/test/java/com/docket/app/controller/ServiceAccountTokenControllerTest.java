package com.docket.app.controller;

import com.docket.app.entity.ServiceAccount;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.service.AuthRateLimiter;
import com.docket.app.service.JwtService;
import com.docket.app.service.ServiceAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAccountTokenControllerTest {
    @Mock private ServiceAccountService serviceAccountService;
    @Mock private JwtService jwtService;
    @Mock private AuthRateLimiter authRateLimiter;
    @Mock private ClientRequestInfo clientRequestInfo;

    @InjectMocks
    private ServiceAccountTokenController controller;

    @Test
    void issuesABearerTokenForValidClientCredentials() {
        ServiceAccount account = new ServiceAccount();
        account.setClientId("sa_123");
        account.setScopes(List.of("documents:read"));
        when(serviceAccountService.findValidAccount("sa_123", "secret", null)).thenReturn(account);
        when(jwtService.generateServiceAccountToken(account)).thenReturn("jwt");

        Map<String, Object> body = controller.token("client_credentials", "sa_123", "secret").getBody();

        assertEquals("jwt", body.get("access_token"));
        assertEquals("Bearer", body.get("token_type"));
        assertEquals("documents:read", body.get("scope"));
    }

    @Test
    void rejectsOtherGrantTypesBeforeCheckingCredentials() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.token("password", "sa_123", "secret"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(serviceAccountService);
    }

    @Test
    void revokedAccountsTokensStopWorking() {
        JwtService real = new JwtService();
        org.springframework.test.util.ReflectionTestUtils.setField(real, "secret",
                "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==");
        org.springframework.test.util.ReflectionTestUtils.setField(real, "serviceAccountTokenTtlMs", 3_600_000L);
        ServiceAccount account = new ServiceAccount();
        account.setClientId("sa_123");
        String token = real.generateServiceAccountToken(account);

        account.setRevokedAt(Instant.now());

        assertFalse(real.isServiceAccountTokenValid(token, account));
    }
}
