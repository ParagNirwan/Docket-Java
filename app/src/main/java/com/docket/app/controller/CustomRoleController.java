package com.docket.app.controller;

import com.docket.app.service.CustomRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CustomRoleController {
    private final CustomRoleService customRoleService;

    public record AssignRequest(UUID customRoleId) {
    }

    @GetMapping("/api/tenants/{slug}/roles")
    public ResponseEntity<Map<String, Object>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "roles", customRoleService.list(slug, authentication),
                "grantablePermissions", CustomRoleService.grantableCodes()));
    }

    @PostMapping("/api/tenants/{slug}/roles")
    public ResponseEntity<CustomRoleService.RoleDTO> create(@PathVariable String slug,
                                                            @RequestBody CustomRoleService.RoleRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customRoleService.create(slug, request, authentication));
    }

    @PutMapping("/api/tenants/{slug}/roles/{roleId}")
    public ResponseEntity<CustomRoleService.RoleDTO> update(@PathVariable String slug, @PathVariable UUID roleId,
                                                            @RequestBody CustomRoleService.RoleRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.ok(customRoleService.update(slug, roleId, request, authentication));
    }

    @DeleteMapping("/api/tenants/{slug}/roles/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID roleId, Authentication authentication) {
        customRoleService.delete(slug, roleId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/tenants/{slug}/members/{membershipId}/custom-role")
    public ResponseEntity<Void> assign(@PathVariable String slug, @PathVariable UUID membershipId,
                                       @RequestBody AssignRequest request, Authentication authentication) {
        customRoleService.assign(slug, membershipId, request.customRoleId(), authentication);
        return ResponseEntity.noContent().build();
    }
}
