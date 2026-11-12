package com.docket.app.service;

import com.docket.app.dto.DocumentComparisonDTO;
import com.docket.app.dto.DocumentInsightsDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentInsightsService {
    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public DocumentInsightsDTO getInsights(String slug, UUID documentId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        Document document = findDocument(tenant.getId(), documentId);
        return new DocumentInsightsDTO(
                document.getAiDocumentType(),
                document.getAiSuggestedTags() == null ? List.of() : document.getAiSuggestedTags(),
                document.getAiFields() == null ? Map.of() : document.getAiFields(),
                document.getKeyDate(),
                document.getAiFieldsReviewedAt()
        );
    }

    @Transactional
    public DocumentInsightsDTO reviewInsights(String slug, UUID documentId, DocumentInsightsDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findDocument(tenant.getId(), documentId);

        if (request != null) {
            document.setAiDocumentType(request.documentType());
            document.setAiSuggestedTags(request.suggestedTags() == null ? List.of() : request.suggestedTags());
            document.setAiFields(request.fields() == null ? Map.of() : request.fields());
            document.setKeyDate(request.keyDate());
            document.setAiFieldsReviewedAt(Instant.now());
            documentRepository.save(document);
        }

        return getInsights(slug, documentId, authentication);
    }

    public DocumentComparisonDTO compareDocuments(String slug, UUID leftDocumentId, UUID rightDocumentId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        Document left = findDocument(tenant.getId(), leftDocumentId);
        Document right = findDocument(tenant.getId(), rightDocumentId);

        Map<String, Object> shared = new LinkedHashMap<>();
        Map<String, Object> differences = new LinkedHashMap<>();
        Map<String, Object> leftFields = left.getAiFields() == null ? Map.of() : left.getAiFields();
        Map<String, Object> rightFields = right.getAiFields() == null ? Map.of() : right.getAiFields();

        for (Map.Entry<String, Object> entry : leftFields.entrySet()) {
            Object rightValue = rightFields.get(entry.getKey());
            if (entry.getValue() == null || rightValue == null) {
                continue;
            }
            if (entry.getValue().equals(rightValue)) {
                shared.put(entry.getKey(), entry.getValue());
            } else {
                differences.put(entry.getKey(), Map.of("left", entry.getValue(), "right", rightValue));
            }
        }

        for (Map.Entry<String, Object> entry : rightFields.entrySet()) {
            if (!leftFields.containsKey(entry.getKey())) {
                differences.put(entry.getKey(), Map.of("left", null, "right", entry.getValue()));
            }
        }

        return new DocumentComparisonDTO(left.getId(), right.getId(), shared, differences);
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private Document findDocument(UUID tenantId, UUID documentId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }
}
