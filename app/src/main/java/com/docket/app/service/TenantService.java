package com.docket.app.service;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Roles;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.docket.app.enums.Status;
import com.docket.app.enums.UserType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;

    public Tenant findBySlug(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    @Transactional
    public Tenant addTenant(CreateTenantRequestDTO tenantDTO, Authentication authentication) {
        String slug = tenantDTO.getSlug();
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization slug is required");
        }

        // Check if the slug is taken
        if (tenantRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization with this slug already exists");
        }

        // Create a new tenant (UUID is generated automatically by @GeneratedValue)
        Tenant tenant = new Tenant();
        tenant.setName(tenantDTO.getTenantName());
        tenant.setSlug(slug);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        // Add the authenticated user as a member of the new tenant
        var user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        var membership = new com.docket.app.entity.TenantMembership();
        membership.setTenantId(tenant.getId());
        membership.setUserId(user.getId());
        membership.setUserType(UserType.INTERNAL);
        membership.setStatus(Status.ACTIVE);
        membership.setRole(Roles.OWNER);
        tenantMembershipRepository.save(membership);

        return tenant;
    }
}
