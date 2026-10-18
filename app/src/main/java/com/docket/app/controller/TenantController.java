package com.docket.app.controller;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.dto.CreateTrayRequestDTO;
import com.docket.app.dto.UpdateTrayDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;


// The "front desk" for organization-related requests from the website.
// Each method here receives a web request and passes it to the TenantService
// for the actual work. All endpoints live under /api/tenants.
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    // POST /api/tenants/create
    // "I want to create a new organization."
    // Takes the org name and slug from the request body, creates the org,
    // and makes the logged-in person the Owner.
    @PostMapping("/create")
    public ResponseEntity<Tenant> createTenant(
            @Valid
            @RequestBody
            CreateTenantRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantService.addTenant(request, authentication));
    }

    // GET /api/tenants/by-slug/{slug}
    // "Tell me about this organization."
    // Looks up the org by its URL name (e.g. "acme-corp") and returns its
    // details — but only if the logged-in person has permission to view it.
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<Tenant> getTenantBySlug(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantService.findBySlug(slug, authentication));
    }

    // GET /api/tenants/by-slug/{slug}/permissions
    // "What am I allowed to do in this organization?"
    // Returns the list of permission codes for the logged-in person.
    // The frontend calls this first when loading a workspace, so it knows
    // which buttons and sections to show.
    @GetMapping("/by-slug/{slug}/permissions")
    public ResponseEntity<Set<String>> getTenantPermissions(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantService.getPermissionCodes(slug, authentication));
    }


    @GetMapping("/{slug}/trays")
    public ResponseEntity<List<Folder>> getTenantTrays(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantService.getTenantTrays(slug, authentication)
        );
    }

    @PostMapping("/{slug}/trays")
    public ResponseEntity<Folder> addTray(
            @Valid
            @RequestBody
            CreateTrayRequestDTO request,
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.addTray(request, slug, authentication));
    }

    @PutMapping("/{slug}/trays/{trayId}")
    public ResponseEntity<Folder> updateTray(
            @Valid
            @RequestBody
            UpdateTrayDTO request,
            @PathVariable String slug,
            @PathVariable UUID trayId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(tenantService.updateTray(trayId, request, slug, authentication));
    }

}