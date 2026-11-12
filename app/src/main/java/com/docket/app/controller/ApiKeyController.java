package com.docket.app.controller;

import com.docket.app.dto.ApiKeyDTO;
import com.docket.app.dto.CreateApiKeyRequestDTO;
import com.docket.app.dto.CreatedApiKeyDTO;
import com.docket.app.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiKeyDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(apiKeyService.list(slug, authentication));
    }

    @PostMapping("/api-keys")
    public ResponseEntity<CreatedApiKeyDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody CreateApiKeyRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.create(slug, request.name(), request.scopes(), authentication));
    }

    @DeleteMapping("/api-keys/{keyId}")
    public ResponseEntity<ApiKeyDTO> revoke(
            @PathVariable String slug,
            @PathVariable UUID keyId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(apiKeyService.revoke(slug, keyId, authentication));
    }
}
