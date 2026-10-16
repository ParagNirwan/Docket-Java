package com.docket.app.service;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Status;
import com.docket.app.enums.TenantStatus;
import com.docket.app.enums.UserType;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

// The organization manager — handles creating orgs, looking them up,
// and fetching a user's permissions within an org.
@Service
@RequiredArgsConstructor
public class TenantService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    // Look up an organization by its URL-friendly name (slug), e.g. "acme-corp".
    // Before returning the org's details, asks the bouncer to verify the
    // logged-in person has permission to even view this organization.
    public Tenant findBySlug(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantAuthorizationService.requirePermission(
                authentication,
                tenant.getId(),
                Permission.TENANT_READ
        );

        return tenant;
    }

    // "What is this person allowed to do in this organization?"
    // Finds the org by its slug, then asks the bouncer for the full list of
    // permission codes. The frontend uses this to decide which buttons to show.
    public Set<String> getPermissionCodes(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        return tenantAuthorizationService.getPermissionCodes(authentication, tenant.getId());
    }

    // Create a brand-new organization.
    // @Transactional means if anything fails halfway through (e.g. the membership
    // save fails), the org creation is also rolled back — nothing half-done gets saved.
    @Transactional
    public Tenant addTenant(CreateTenantRequestDTO tenantDTO, Authentication authentication) {

        // Make sure the person is logged in.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // The slug is the URL-friendly name (e.g. "acme-corp"). It's required.
        String slug = tenantDTO.getSlug();
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization slug is required");
        }

        // Make sure nobody else has already taken this slug — like checking
        // if a username is available.
        if (tenantRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization with this slug already exists");
        }

        // Create the new organization in the database and mark it as active.
        Tenant tenant = new Tenant();
        tenant.setName(tenantDTO.getTenantName());
        tenant.setSlug(slug);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        // Automatically make the person who created this org the Owner.
        // They get the highest role and an active membership right away.
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
