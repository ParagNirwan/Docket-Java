package com.docket.app.controller;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;
    @PostMapping("/create")
    public ResponseEntity<Tenant> createTenant(
            @Valid
            @RequestBody
            CreateTenantRequestDTO request,
            Authentication authentication
    ) {
       return ResponseEntity.ok(tenantService.addTenant(request, authentication));
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<Tenant> getTenantBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(tenantService.findBySlug(slug));
    }
}