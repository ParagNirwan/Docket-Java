package com.docket.app.service;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.dto.CreateTrayRequestDTO;
import com.docket.app.dto.UpdateTrayDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.*;
import com.docket.app.repository.FolderRepository;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

// The organization manager — handles creating orgs, looking them up,
// and fetching a user's permissions within an org.
@Service
@RequiredArgsConstructor
public class TenantService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderRepository folderRepository;

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

        if(!createDefaultFoldersForTenant(tenant.getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create default folders for tenant. Contact Support for assistance.");
        } else return tenant;
    }


    //Get Tenant Trays
    public List<Folder> getTenantTrays(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        Roles userRole = tenantAuthorizationService.getMembershipRole(authentication, tenant.getId());
        List<Folder> trays = folderRepository.findByTenantIdAndIsTrayIsTrue(tenant.getId());
        if (userRole == Roles.MEMBER || userRole == Roles.MANAGER) {
            trays.removeIf(t -> t.getVisibility() == Visibility.ADMIN_ONLY);
        }
        return trays;

    }

    //Create Tenant Tray
    public Folder addTray(CreateTrayRequestDTO request, String slug, Authentication authentication) {
        //Tenant Check
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantAuthorizationService.requirePermission(
                authentication,
                tenant.getId(),
                Permission.TRAYS_CREATE
        );

        Folder folder = new Folder();
        folder.setTenantId(tenant.getId());
        folder.setName(request.getName());
        folder.setDescription(request.getDescription());
        folder.setIsTray(true);
        folder.setIsDefault(false);
        folder.setVisibility(request.getVisibility());
        folder.setSortOrder(0);
        folder.setCreatedBy(userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")).getId());
        folder.setCreatedAt(java.time.Instant.now());
        folder.setUpdatedAt(java.time.Instant.now());

        return folderRepository.save(folder);
    }

    //Update Tenant Tray
    public Folder updateTray(UUID trayId, UpdateTrayDTO request, String slug, Authentication authentication) {

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantAuthorizationService.requirePermission(
                authentication,
                tenant.getId(),
                Permission.TRAYS_UPDATE
        );

        Folder folder = folderRepository.findByTenantIdAndIsTrayIsTrueAndId(tenant.getId(), trayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tray not found"));
        if (folder.getIsDefault()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Default trays cannot be modified");
        }

        folder.setName(request.getName());
        folder.setDescription(request.getDescription());
        folder.setVisibility(request.getVisibility());
        folder.setSortOrder(request.getSortOrder());
        folder.setUpdatedAt(java.time.Instant.now());
        return folderRepository.save(folder);
    }

    public Boolean createDefaultFoldersForTenant(UUID tenantId, UUID userId) {
        try {
            Folder inbox = new Folder();
            inbox.setTenantId(tenantId);
            inbox.setName("Inbox");
            inbox.setDescription("Default Inbox Tray");
            inbox.setIsTray(true);
            inbox.setIsDefault(true);
            inbox.setVisibility(Visibility.ALL_MEMBERS);
            inbox.setSortOrder(1);
            inbox.setCreatedBy(userId);
            inbox.setCreatedAt(java.time.Instant.now());
            inbox.setUpdatedAt(java.time.Instant.now());
            folderRepository.save(inbox);

            Folder archive = new Folder();
            archive.setTenantId(tenantId);
            archive.setName("Archive");
            archive.setDescription("Default Archive Tray");
            archive.setIsTray(true);
            archive.setIsDefault(true);
            archive.setVisibility(Visibility.ALL_MEMBERS);
            archive.setSortOrder(2);
            archive.setCreatedBy(userId);
            archive.setCreatedAt(java.time.Instant.now());
            archive.setUpdatedAt(java.time.Instant.now());
            folderRepository.save(archive);

            Folder trash = new Folder();
            trash.setTenantId(tenantId);
            trash.setName("Trash");
            trash.setDescription("Default Trash Tray");
            trash.setIsTray(true);
            trash.setIsDefault(true);
            trash.setVisibility(Visibility.ADMIN_ONLY);
            trash.setSortOrder(3);
            trash.setCreatedBy(userId);
            trash.setCreatedAt(java.time.Instant.now());
            trash.setUpdatedAt(java.time.Instant.now());
            folderRepository.save(trash);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}