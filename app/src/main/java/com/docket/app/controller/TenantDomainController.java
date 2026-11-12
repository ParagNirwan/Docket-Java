package com.docket.app.controller;

import com.docket.app.dto.CreateTenantDomainRequestDTO;
import com.docket.app.dto.TenantDomainDTO;
import com.docket.app.dto.UpdateTenantDomainRequestDTO;
import com.docket.app.dto.VerifyTenantDomainRequestDTO;
import com.docket.app.service.TenantDomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{slug}/domains")
@RequiredArgsConstructor
public class TenantDomainController {
    private final TenantDomainService tenantDomainService;

    @GetMapping
    public ResponseEntity<List<TenantDomainDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(tenantDomainService.list(slug, authentication));
    }

    @PostMapping
    public ResponseEntity<TenantDomainDTO> add(
            @PathVariable String slug,
            @Valid @RequestBody CreateTenantDomainRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantDomainService.add(slug, request.domain(), authentication));
    }

    @PostMapping("/verify")
    public ResponseEntity<TenantDomainDTO> verify(
            @PathVariable String slug,
            @Valid @RequestBody VerifyTenantDomainRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantDomainService.verify(slug, request.domain(), request.token(), authentication));
    }

    @PutMapping("/{domain}")
    public ResponseEntity<TenantDomainDTO> update(
            @PathVariable String slug,
            @PathVariable String domain,
            @RequestBody UpdateTenantDomainRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantDomainService.update(slug, domain, request, authentication));
    }

    @DeleteMapping("/{domain}")
    public ResponseEntity<Void> remove(
            @PathVariable String slug,
            @PathVariable String domain,
            Authentication authentication
    ) {
        tenantDomainService.remove(slug, domain, authentication);
        return ResponseEntity.noContent().build();
    }
}
