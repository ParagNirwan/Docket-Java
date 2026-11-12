package com.docket.app.service;

import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SsoService {
    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${docket.sso.google.client-id:}")
    private String googleClientId;
    @Value("${docket.sso.google.client-secret:}")
    private String googleClientSecret;
    @Value("${docket.sso.google.redirect-uri:http://localhost:5173/auth/sso/callback}")
    private String googleRedirectUri;
    @Value("${docket.sso.microsoft.client-id:}")
    private String microsoftClientId;
    @Value("${docket.sso.microsoft.client-secret:}")
    private String microsoftClientSecret;
    @Value("${docket.sso.microsoft.redirect-uri:http://localhost:5173/auth/sso/callback}")
    private String microsoftRedirectUri;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = RestClient.builder().build();
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

        Map<String, Object> claims = decodeJwtPayload(idToken);
        String subject = requireString(claims, "sub");
        String email = firstNonBlank(
                stringValue(claims.get("email")),
                stringValue(claims.get("preferred_username")),
                stringValue(claims.get("upn"))
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

    public boolean isConfigured(String provider) {
        return providerConfig(provider, null) != null;
    }

    private User createNewUser(String email, Map<String, Object> claims, String provider, String subject) {
        String fullName = firstNonBlank(
                stringValue(claims.get("name")),
                stringValue(claims.get("given_name")),
                stringValue(claims.get("family_name"))
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

    private Map<String, Object> decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed identity token");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(decoded, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not decode provider identity token", e);
        }
    }

    private ProviderConfig providerConfig(String provider, String redirectUri) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        ProviderConfig config = switch (normalized) {
            case "google" -> new ProviderConfig(
                    "google",
                    googleClientId,
                    googleClientSecret,
                    redirectUri == null || redirectUri.isBlank() ? googleRedirectUri : redirectUri,
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "openid email profile",
                    null);
            case "microsoft", "azure", "azure-ad", "microsoft-entra" -> new ProviderConfig(
                    "microsoft",
                    microsoftClientId,
                    microsoftClientSecret,
                    redirectUri == null || redirectUri.isBlank() ? microsoftRedirectUri : redirectUri,
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

    private String requireString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing claim: " + key);
        }
        return value.toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
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
