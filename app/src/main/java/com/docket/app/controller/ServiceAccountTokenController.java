package com.docket.app.controller;

import com.docket.app.entity.ServiceAccount;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.service.AuthRateLimiter;
import com.docket.app.service.JwtService;
import com.docket.app.service.ServiceAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

// OAuth 2.0 client-credentials grant (RFC 6749 §4.4) for service accounts: swap the client
// ID and secret for a short-lived bearer token to call /api/public/**. Public (under
// /api/auth/**); the credentials authenticate the request.
@RestController
@RequiredArgsConstructor
public class ServiceAccountTokenController {
    private final ServiceAccountService serviceAccountService;
    private final JwtService jwtService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientRequestInfo clientRequestInfo;

    @Value("${jwt.service-account-token-ttl-ms:3600000}")
    private long tokenTtlMs;

    @PostMapping(value = "/api/auth/service-accounts/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret
    ) {
        authRateLimiter.check("sa-token:ip:" + clientRequestInfo.ip(), 30, Duration.ofMinutes(1));
        if (!"client_credentials".equals(grantType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
        }
        ServiceAccount account = serviceAccountService.findValidAccount(clientId, clientSecret, null);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of(
                        "access_token", jwtService.generateServiceAccountToken(account),
                        "token_type", "Bearer",
                        "expires_in", tokenTtlMs / 1000,
                        "scope", String.join(" ", account.getScopes() == null ? java.util.List.of() : account.getScopes())));
    }
}
