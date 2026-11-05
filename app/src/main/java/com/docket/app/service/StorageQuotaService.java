package com.docket.app.service;

import com.docket.app.dto.TenantUsageDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantUsage;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {
    private final TenantRepository tenantRepository;
    private final TenantUsageRepository tenantUsageRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // Checks that adding additionalBytes won't exceed the organization's quota.
    // If approaching or exceeding, notifies admins.
    public void assertQuota(UUID tenantId, long additionalBytes) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        if (tenant.getStorageQuotaBytes() == null) {
            return; // Unlimited
        }

        long currentUsed = tenantUsageRepository.findByTenantId(tenantId)
                .map(TenantUsage::getStorageBytesUsed)
                .orElse(0L);

        long newTotal = currentUsed + additionalBytes;
        long quota = tenant.getStorageQuotaBytes();

        if (newTotal > quota) {
            notificationService.notifyPermissionHolders(tenantId, Permission.TENANT_UPDATE,
                    NotificationType.STORAGE_QUOTA_EXCEEDED,
                    "Storage quota exceeded",
                    "Your organization has exceeded its storage quota of " + (quota / (1024 * 1024)) + " MB.",
                    NotificationService.workspaceLink(tenant.getSlug(), "settings", "general"));
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded (" + (quota / (1024 * 1024)) + " MB limit)");
        }

        // 80% warning
        if (currentUsed < 0.8 * quota && newTotal >= 0.8 * quota) {
            notificationService.notifyPermissionHolders(tenantId, Permission.TENANT_UPDATE,
                    NotificationType.STORAGE_QUOTA_WARNING,
                    "Storage quota approaching limit",
                    "Your organization has used over 80% of its storage quota.",
                    NotificationService.workspaceLink(tenant.getSlug(), "settings", "general"));
        }
    }

    @Transactional
    public void recordUsage(UUID tenantId, long deltaBytes, long deltaDocs) {
        if (tenantUsageRepository.adjustUsage(tenantId, deltaBytes, deltaDocs) == 0) {
            TenantUsage usage = new TenantUsage(tenantId, Math.max(0L, deltaBytes), Math.max(0L, deltaDocs), java.time.Instant.now());
            tenantUsageRepository.save(usage);
        }
    }

    public TenantUsageDTO getUsage(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_READ);

        TenantUsage usage = tenantUsageRepository.findByTenantId(tenant.getId())
                .orElseGet(() -> new TenantUsage(tenant.getId(), 0L, 0L, java.time.Instant.now()));

        return new TenantUsageDTO(usage.getStorageBytesUsed(), tenant.getStorageQuotaBytes(), usage.getDocumentCount());
    }

    @Transactional
    public TenantUsageDTO updateQuota(String slug, Long quotaBytes, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        if (quotaBytes != null && quotaBytes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quota must be greater than zero, or null for unlimited");
        }

        tenant.setStorageQuotaBytes(quotaBytes);
        tenantRepository.save(tenant);

        auditService.record(authentication, tenant.getId(), AuditAction.STORAGE_QUOTA_UPDATED, "TENANT", tenant.getId(),
                Map.of("quotaBytes", quotaBytes == null ? "unlimited" : quotaBytes.toString()));

        return getUsage(slug, authentication);
    }
}

