package com.docket.app.service;

import com.docket.app.config.DocketSsoProperties;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsoService {
    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final DocketSsoProperties ssoProperties;

    private RestClient restClient;
    private final Map<String, ConfigurableJWTProcessor<SecurityContext>> jwtProcessors = new ConcurrentHashMap<>();
    private final Map<String, JWKSet> jwksCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService jwksRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sso-jwks-refresh");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void init() {
        restClient = RestClient.builder().build();
        initializeJwtProcessors();
        scheduleJwksRefresh();
    }

    @PreDestroy
    void destroy() {
        jwksRefreshExecutor.shutdown();
        try {
            if (!jwksRefreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                jwksRefreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            jwksRefreshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void initializeJwtProcessors() {
        // Google JWKS
        if (ssoProperties.getGoogle().isConfigured()) {
            setupProvider("google", "https://www.googleapis.com/oauth2/v3/certs",
                    "https://accounts.google.com", ssoProperties.getGoogle().getClientId());
        }

        // Microsoft JWKS
        if (ssoProperties.getMicrosoft().isConfigured()) {
            setupProvider("microsoft", "https://login.microsoftonline.com/common/discovery/v2.0/keys",
                    "https://login.microsoftonline.com/common/v2.0", ssoProperties.getMicrosoft().getClientId());
        }
    }

    private void setupProvider(String provider, String jwksUrl, String expectedIssuer, String expectedAudience) {
        try {
            JWKSet jwkSet = fetchJwks(jwksUrl);
            jwksCache.put(provider, jwkSet);

            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(keySelector);
            processor.setJWTClaimsSetVerifier((claims, context) -> {
                // Verify issuer
                if (!expectedIssuer.equals(claims.getIssuer())) {
                    throw new com.nimbusds.jwt.proc.BadJWTException("Invalid issuer: " + claims.getIssuer());
                }
                // Verify audience
                if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
                    throw new com.nimbusds.jwt.proc.BadJWTException("Invalid audience: " + claims.getAudience());
                }
                // Verify expiration
                if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                    throw new com.nimbusds.jwt.proc.BadJWTException("Token expired");
                }
                // Verify not-before
                if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().after(new Date())) {
                    throw new com.nimbusds.jwt.proc.BadJWTException("Token not yet valid");
                }
            });

            jwtProcessors.put(provider, processor);
            log.info("Initialized SSO JWT processor for provider: {}", provider);
        } catch (Exception e) {
            log.error("Failed to initialize SSO JWT processor for provider {}: {}", provider, e.getMessage());
        }
    }

    private JWKSet fetchJwks(String jwksUrl) throws Exception {
        URL url = new URL(jwksUrl);
        return JWKSet.load(url);
    }

    private void scheduleJwksRefresh() {
        // Refresh JWKS every 12 hours
        jwksRefreshExecutor.scheduleAtFixedRate(this::refreshAllJwks, 12, 12, TimeUnit.HOURS);
    }

    private void refreshAllJwks() {
        log.debug("Refreshing JWKS cache for all providers");
        jwtProcessors.forEach((provider, processor) -> {
            try {
                String jwksUrl = switch (provider) {
                    case "google" -> "https://www.googleapis.com/oauth2/v3/certs";
                    case "microsoft" -> "https://login.microsoftonline.com/common/discovery/v2.0/keys";
                    default -> null;
                };
                if (jwksUrl != null) {
                    JWKSet newJwkSet = fetchJwks(jwksUrl);
                    jwksCache.put(provider, newJwkSet);
                    // Re-create processor with new keys
                    JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(newJwkSet);
                    JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
                    processor.setJWSKeySelector(keySelector);
                    log.info("Refreshed JWKS for provider: {}", provider);
                }
            } catch (Exception e) {
                log.warn("Failed to refresh JWKS for provider {}: {}", provider, e.getMessage());
            }
        });
    }

    public String buildAuthUrl(String provider, String redirectUri, String state) {
        ProviderConfig config = providerConfig(provider, redirectUri);
        if (!config.configured()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SSO for " + provider + " is not configured");
        }
        String safeState = state == null || state.isBlank() ? UUID.randomUUID().toString() : state;
        return UriComponentsBuilder.fromUriString(config.authUri())
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", config.scope())
                .queryParam("state", safeState)
                .queryParam("prompt", "select_account")
                .build()
                .toUriString();
    }

    public SessionService.IssuedSession login(String provider, String code, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authorization code");
        }
        ProviderConfig config = providerConfig(provider, redirectUri);
        if (!config.configured()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SSO for " + provider + " is not configured");
        }
        Map<String, Object> tokenResponse = exchangeCodeForToken(config, code);

        String idToken = (String) tokenResponse.get("id_token");
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The provider did not return an identity token");
        }

        // Validate and decode ID token using JWKS
        JWTClaimsSet claims = validateIdToken(provider, idToken, config);

        String subject = claims.getSubject();
        String email = firstNonBlank(
                getStringClaimSafe(claims, "email"),
                getStringClaimSafe(claims, "preferred_username"),
                getStringClaimSafe(claims, "upn")
        );
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The identity provider did not return an email address");
        }

        User user = userIdentityService.findUserByIdentity(config.provider(), subject)
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(() -> createNewUser(email, claims, config.provider(), subject));

        if (userIdentityService.findUserByIdentity(config.provider(), subject).isEmpty()) {
            userIdentityService.linkIdentity(user, config.provider(), subject, email);
            auditService.recordAccountEvent(user.getId(), AuditAction.SSO_IDENTITY_LINKED, "USER_IDENTITY", user.getId(),
                    Map.of("provider", config.provider(), "subject", subject));
        }

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
        }

        return sessionService.start(user);
    }

    private JWTClaimsSet validateIdToken(String provider, String idToken, ProviderConfig config) {
        ConfigurableJWTProcessor<SecurityContext> processor = jwtProcessors.get(provider);
        if (processor == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SSO provider not initialized: " + provider);
        }

        try {
            return processor.process(idToken, null);
        } catch (com.nimbusds.jwt.proc.BadJWTException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid ID token: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("JWT validation failed for provider {}: {}", provider, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed", e);
        }
    }

    public boolean isConfigured(String provider) {
        ProviderConfig config = providerConfig(provider, null);
        return config != null && config.configured();
    }

    private User createNewUser(String email, JWTClaimsSet claims, String provider, String subject) {
        String fullName = firstNonBlank(
                getStringClaimSafe(claims, "name"),
                getStringClaimSafe(claims, "given_name"),
                getStringClaimSafe(claims, "family_name")
        );
        if (fullName == null || fullName.isBlank()) {
            fullName = email.substring(0, email.indexOf('@'));
        }
        User user = new User(fullName, email.toLowerCase(Locale.ROOT), passwordEncoder.encode(TokenHasher.newToken()));
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    private Map<String, Object> exchangeCodeForToken(ProviderConfig config, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", config.redirectUri());

        return restClient.post()
                .uri(config.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (request, response) -> {
                            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO token exchange failed: " + body);
                        })
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private ProviderConfig providerConfig(String provider, String redirectUri) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        ProviderConfig config = switch (normalized) {
            case "google" -> new ProviderConfig(
                    "google",
                    ssoProperties.getGoogle().getClientId(),
                    ssoProperties.getGoogle().getClientSecret(),
                    redirectUri == null || redirectUri.isBlank() ? ssoProperties.getGoogle().getRedirectUri() : redirectUri,
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "openid email profile",
                    null);
            case "microsoft", "azure", "azure-ad", "microsoft-entra" -> new ProviderConfig(
                    "microsoft",
                    ssoProperties.getMicrosoft().getClientId(),
                    ssoProperties.getMicrosoft().getClientSecret(),
                    redirectUri == null || redirectUri.isBlank() ? ssoProperties.getMicrosoft().getRedirectUri() : redirectUri,
                    "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    "openid profile email User.Read",
                    null);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported SSO provider: " + provider);
        };
        if (!config.configured()) {
            return config;
        }
        return config;
    }

    private String getStringClaimSafe(JWTClaimsSet claims, String claimName) {
        try {
            return claims.getStringClaim(claimName);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ProviderConfig(
            String provider,
            String clientId,
            String clientSecret,
            String redirectUri,
            String authUri,
            String tokenUri,
            String scope,
            String userInfoUri
    ) {
        boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}