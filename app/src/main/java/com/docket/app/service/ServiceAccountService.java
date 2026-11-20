package com.docket.app.service;

import com.docket.app.dto.CreatedServiceAccountDTO;
import com.docket.app.dto.CreateServiceAccountRequestDTO;
import com.docket.app.dto.ServiceAccountDTO;
import com.docket.app.entity.ServiceAccount;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.ServiceAccountRepository;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceAccountService {
    private final TenantRepository tenantRepository;
    private final ServiceAccountRepository serviceAccountRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    public List<ServiceAccountDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        return serviceAccountRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CreatedServiceAccountDTO create(String slug, CreateServiceAccountRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);

        String clientId = generateClientId();
        String clientSecret = generateClientSecret();
        ServiceAccount serviceAccount = new ServiceAccount();
        serviceAccount.setTenantId(tenant.getId());
        serviceAccount.setName(request.name());
        serviceAccount.setClientId(clientId);
        serviceAccount.setClientSecretHash(hash(clientSecret));
        serviceAccount.setScopes(request.scopes() == null ? List.of() : request.scopes().stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        serviceAccount.setCreatedBy(currentUserId(authentication));
        serviceAccount.setCreatedAt(Instant.now());
        serviceAccountRepository.save(serviceAccount);

        auditService.record(authentication, tenant.getId(), AuditAction.SERVICE_ACCOUNT_CREATED, "SERVICE_ACCOUNT", serviceAccount.getId(),
                java.util.Map.of("name", serviceAccount.getName(), "clientId", serviceAccount.getClientId()));
        return new CreatedServiceAccountDTO(toDto(serviceAccount), clientSecret);
    }

    @Transactional
    public ServiceAccountDTO revoke(String slug, UUID accountId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        ServiceAccount serviceAccount = serviceAccountRepository.findByTenantIdAndId(tenant.getId(), accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service account not found"));
        serviceAccount.setRevokedAt(Instant.now());
        serviceAccountRepository.save(serviceAccount);
        auditService.record(authentication, tenant.getId(), AuditAction.SERVICE_ACCOUNT_REVOKED, "SERVICE_ACCOUNT", serviceAccount.getId(),
                java.util.Map.of("name", serviceAccount.getName()));
        return toDto(serviceAccount);
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash service account secret", e);
        }
    }

    public ServiceAccount findValidAccount(String clientId, String clientSecret, UUID tenantId) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service account credentials");
        }
        ServiceAccount account = serviceAccountRepository.findByClientId(clientId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service account"));
        if (!hash(clientSecret.trim()).equals(account.getClientSecretHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service account credentials");
        }
        if (account.getRevokedAt() != null || account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service account is no longer valid");
        }
        if (tenantId != null && !account.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service account does not match tenant");
        }
        // Update last used timestamp
        account.setLastUsedAt(Instant.now());
        serviceAccountRepository.save(account);
        return account;
    }

    public ServiceAccount findByClientId(String clientId) {
        return serviceAccountRepository.findByClientId(clientId).orElse(null);
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

    private String generateClientId() {
        return "dkt_sa_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateClientSecret() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return "dkt_sa_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private ServiceAccountDTO toDto(ServiceAccount account) {
        return new ServiceAccountDTO(
                account.getId(),
                account.getName(),
                account.getClientId(),
                account.getScopes() == null ? List.of() : account.getScopes(),
                account.getCreatedAt(),
                account.getLastUsedAt(),
                account.getExpiresAt(),
                account.getRevokedAt()
        );
    }
}