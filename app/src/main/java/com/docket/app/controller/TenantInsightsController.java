package com.docket.app.controller;

import com.docket.app.dto.TenantInsightsDTO;
import com.docket.app.service.TenantInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{slug}/insights")
@RequiredArgsConstructor
public class TenantInsightsController {
    private final TenantInsightsService tenantInsightsService;

    @GetMapping
    public ResponseEntity<TenantInsightsDTO> insights(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(tenantInsightsService.insights(slug, authentication));
    }
}
