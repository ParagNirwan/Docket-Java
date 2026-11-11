package com.docket.app.service;

import com.docket.app.dto.AiSettingsDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// What an organization lets the AI do with its documents
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    public AiSettingsDTO get(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_READ);
        return new AiSettingsDTO(tenant.isAiSummariesEnabled());
    }

    // Switching summaries off also deletes the ones already written. An organization that
    // turns this off for privacy shouldn't have to wonder what is still stored.
    @Transactional
    public AiSettingsDTO update(String slug, AiSettingsDTO settings, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        boolean changed = tenant.isAiSummariesEnabled() != settings.summariesEnabled();
        tenant.setAiSummariesEnabled(settings.summariesEnabled());
        tenantRepository.save(tenant);

        if (changed) {
            if (!settings.summariesEnabled()) {
                documentRepository.clearAiSummariesForTenant(tenant.getId());
            }
            auditService.record(authentication, tenant.getId(), AuditAction.AI_SETTINGS_UPDATED, "TENANT",
                    tenant.getId(), Map.of("summariesEnabled", String.valueOf(settings.summariesEnabled())));
        }
        return new AiSettingsDTO(tenant.isAiSummariesEnabled());
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
