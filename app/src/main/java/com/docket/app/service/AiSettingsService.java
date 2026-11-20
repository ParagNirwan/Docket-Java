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
        return dto(tenant);
    }

    // Switching summaries off also deletes the ones already written. An organization that
    // turns this off for privacy shouldn't have to wonder what is still stored.
    @Transactional
    public AiSettingsDTO update(String slug, AiSettingsDTO settings, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        boolean changed = tenant.isAiSummariesEnabled() != settings.summariesEnabled();
        tenant.setAiSummariesEnabled(settings.summariesEnabled());

        boolean classificationChanged = settings.classificationEnabled() != null
                && settings.classificationEnabled() != tenant.isAiClassificationEnabled();
        boolean autoApplyChanged = settings.autoApplyTags() != null
                && settings.autoApplyTags() != tenant.isAiAutoApplyTags();
        if (settings.classificationEnabled() != null) tenant.setAiClassificationEnabled(settings.classificationEnabled());
        if (settings.autoApplyTags() != null) tenant.setAiAutoApplyTags(settings.autoApplyTags());
        boolean tuningChanged = applyTuning(tenant, settings);
        tenantRepository.save(tenant);

        if (changed && !settings.summariesEnabled()) {
            documentRepository.clearAiSummariesForTenant(tenant.getId());
        }
        if (classificationChanged && !tenant.isAiClassificationEnabled()) {
            documentRepository.clearAiClassificationsForTenant(tenant.getId());
        }
        if (changed || classificationChanged || autoApplyChanged || tuningChanged) {
            auditService.record(authentication, tenant.getId(), AuditAction.AI_SETTINGS_UPDATED, "TENANT",
                    tenant.getId(), Map.of(
                            "summariesEnabled", String.valueOf(tenant.isAiSummariesEnabled()),
                            "classificationEnabled", String.valueOf(tenant.isAiClassificationEnabled()),
                            "autoApplyTags", String.valueOf(tenant.isAiAutoApplyTags()),
                            "maxDistance", String.valueOf(tenant.getAiMaxDistance()),
                            "fallbackMaxDistance", String.valueOf(tenant.getAiFallbackMaxDistance()),
                            "rerankMinScore", String.valueOf(tenant.getAiRerankMinScore())));
        }
        return dto(tenant);
    }

    private static AiSettingsDTO dto(Tenant tenant) {
        return new AiSettingsDTO(tenant.isAiSummariesEnabled(), tenant.isAiClassificationEnabled(), tenant.isAiAutoApplyTags(),
                tenant.getAiMaxDistance(), tenant.getAiFallbackMaxDistance(), tenant.getAiRerankMinScore(), null);
    }

    // Cosine distance is 0..2; rerank scores are 0..1. The fallback bound has to be at least
    // as loose as the main one, or it would never add anything.
    static boolean applyTuning(Tenant tenant, AiSettingsDTO settings) {
        if (Boolean.TRUE.equals(settings.resetRetrievalTuning())) {
            boolean had = tenant.getAiMaxDistance() != null || tenant.getAiFallbackMaxDistance() != null
                    || tenant.getAiRerankMinScore() != null;
            tenant.setAiMaxDistance(null);
            tenant.setAiFallbackMaxDistance(null);
            tenant.setAiRerankMinScore(null);
            return had;
        }
        Double max = settings.maxDistance() != null ? settings.maxDistance() : tenant.getAiMaxDistance();
        Double fallback = settings.fallbackMaxDistance() != null ? settings.fallbackMaxDistance() : tenant.getAiFallbackMaxDistance();
        Double rerank = settings.rerankMinScore() != null ? settings.rerankMinScore() : tenant.getAiRerankMinScore();
        require(max == null || (max > 0 && max <= 2), "Max distance must be between 0 and 2");
        require(fallback == null || (fallback > 0 && fallback <= 2), "Fallback distance must be between 0 and 2");
        require(max == null || fallback == null || fallback >= max, "The fallback distance must be at least the max distance");
        require(rerank == null || (rerank >= 0 && rerank <= 1), "Rerank minimum score must be between 0 and 1");
        boolean changed = !java.util.Objects.equals(max, tenant.getAiMaxDistance())
                || !java.util.Objects.equals(fallback, tenant.getAiFallbackMaxDistance())
                || !java.util.Objects.equals(rerank, tenant.getAiRerankMinScore());
        tenant.setAiMaxDistance(max);
        tenant.setAiFallbackMaxDistance(fallback);
        tenant.setAiRerankMinScore(rerank);
        return changed;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
