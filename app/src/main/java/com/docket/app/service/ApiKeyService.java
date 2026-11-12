package com.docket.app.service;

import com.docket.app.dto.ApiKeyDTO;
import com.docket.app.dto.CreatedApiKeyDTO;
import com.docket.app.entity.ApiKey;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.ApiKeyRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final TenantRepository tenantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    public List<ApiKeyDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CreatedApiKeyDTO create(String slug, String name, List<String> scopes, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);

        String secret = generateSecret();
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenant.getId());
        apiKey.setName(name == null || name.isBlank() ? "API key" : name.trim());
        apiKey.setKeyHash(hash(secret));
        apiKey.setPrefix(secret.substring(0, Math.min(8, secret.length())));
        apiKey.setScopes(scopes == null ? List.of() : scopes.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        apiKey.setCreatedBy(currentUserId(authentication));
        apiKey.setCreatedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        auditService.record(authentication, tenant.getId(), AuditAction.API_KEY_CREATED, "API_KEY", apiKey.getId(),
                java.util.Map.of("name", apiKey.getName(), "prefix", apiKey.getPrefix()));
        return new CreatedApiKeyDTO(toDto(apiKey), secret);
    }

    @Transactional
    public ApiKeyDTO revoke(String slug, UUID keyId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        ApiKey apiKey = apiKeyRepository.findByTenantIdAndId(tenant.getId(), keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        auditService.record(authentication, tenant.getId(), AuditAction.API_KEY_REVOKED, "API_KEY", apiKey.getId(),
                java.util.Map.of("name", apiKey.getName()));
        return toDto(apiKey);
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash API key", e);
        }
    }

    public ApiKey findValidKey(String rawKey, UUID tenantId) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing API key");
        }
        String hashed = hash(rawKey.trim());
        ApiKey key = apiKeyRepository.findByKeyHash(hashed)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
        if (key.getRevokedAt() != null || key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key is no longer valid");
        }
        if (tenantId != null && !key.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key does not match tenant");
        }
        return key;
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private String generateSecret() {
        return "dkt_live_" + UUID.randomUUID().toString().replace("-", "") + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ApiKeyDTO toDto(ApiKey apiKey) {
        return new ApiKeyDTO(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getPrefix(),
                apiKey.getScopes() == null ? List.of() : apiKey.getScopes(),
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getRevokedAt()
        );
    }
}
