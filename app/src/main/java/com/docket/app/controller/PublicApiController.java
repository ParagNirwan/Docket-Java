package com.docket.app.controller;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.entity.ApiKey;
import com.docket.app.entity.ServiceAccount;
import com.docket.app.service.ApiKeyService;
import com.docket.app.service.DocumentService;
import com.docket.app.service.ServiceAccountService;
import com.docket.app.service.TenantAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicApiController {
    private final ApiKeyService apiKeyService;
    private final ServiceAccountService serviceAccountService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final DocumentService documentService;

    @GetMapping("/tenants/{slug}/documents/{docId}")
    public ResponseEntity<DocumentResponseDTO> getDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        Object principal = authentication.getPrincipal();
        UUID tenantId;

        if (principal instanceof ApiKey apiKey) {
            tenantId = apiKey.getTenantId();
        } else if (principal instanceof ServiceAccount serviceAccount) {
            tenantId = serviceAccount.getTenantId();
        } else {
            throw new IllegalStateException("Unexpected authentication principal type");
        }

        tenantAuthorizationService.requirePermission(authentication, tenantId, com.docket.app.enums.Permission.DOCUMENTS_READ);
        return ResponseEntity.ok(documentService.getDocumentById(slug, docId, authentication));
    }
}
