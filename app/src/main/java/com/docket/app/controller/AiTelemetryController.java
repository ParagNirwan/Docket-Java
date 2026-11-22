package com.docket.app.controller;

import com.docket.app.dto.AiRateLimitTelemetryDTO;
import com.docket.app.dto.AiRetrievalTelemetryDTO;
import com.docket.app.service.AiTelemetryService;
import com.docket.app.enums.Permission;
import com.docket.app.service.TenantAuthorizationService;
import com.docket.app.repository.TenantRepository;
import com.docket.app.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tenants/{slug}/ai/telemetry")
@RequiredArgsConstructor
public class AiTelemetryController {

    private final AiTelemetryService aiTelemetryService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final TenantRepository tenantRepository;

    @GetMapping("/retrieval")
    public ResponseEntity<AiRetrievalTelemetryDTO> retrievalTelemetry(
            @PathVariable String slug,
            Authentication authentication
    ) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        // Only tenant admins/owners can view telemetry
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.TENANT_UPDATE);

        return ResponseEntity.ok(aiTelemetryService.getRetrievalTelemetry(tenant.getId()));
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<AiRateLimitTelemetryDTO> rateLimitTelemetry(
            @PathVariable String slug,
            Authentication authentication
    ) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.TENANT_UPDATE);

        return ResponseEntity.ok(aiTelemetryService.getRateLimitTelemetry(tenant.getId()));
    }
}