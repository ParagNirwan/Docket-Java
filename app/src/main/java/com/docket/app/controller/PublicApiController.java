package com.docket.app.controller;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.entity.ApiKey;
import com.docket.app.entity.User;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.ApiKeyService;
import com.docket.app.service.DocumentService;
import com.docket.app.service.TenantAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicApiController {
    private final ApiKeyService apiKeyService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final DocumentService documentService;
    private final UserRepository userRepository;

    @GetMapping("/tenants/{slug}/documents/{docId}")
    public ResponseEntity<DocumentResponseDTO> getDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            HttpServletRequest request
    ) {
        String rawKey = request.getHeader("X-API-Key");
        ApiKey apiKey = apiKeyService.findValidKey(rawKey, null);
        User actor = userRepository.findById(apiKey.getCreatedBy())
                .orElseThrow(() -> new RuntimeException("API key owner not found"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(actor.getEmail(), null, List.of());
        tenantAuthorizationService.requirePermission(authentication, apiKey.getTenantId(), com.docket.app.enums.Permission.DOCUMENTS_READ);
        return ResponseEntity.ok(documentService.getDocumentById(slug, docId, authentication));
    }
}
