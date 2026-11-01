package com.docket.app.service;

import com.docket.app.entity.TenantMembership;
import com.docket.app.enums.*;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// The "bouncer" service — every time someone tries to do something inside an
// organization, this service checks their identity, membership, and permissions.
@Service
@RequiredArgsConstructor
public class TenantAuthorizationService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;

    // "Is this person allowed to perform this action in this organization?"
    // First finds who they are and confirms they're a member, then checks the
    // rulebook to see if their role grants the requested permission.
    // If not, blocks them with a "you don't have permission" error.
    public void requirePermission(
            Authentication authentication,
            UUID tenantId,
            Permission permission
    ) {
        var membership = findActiveMembership(authentication, tenantId);

        if (permission == null || !RolePermissions.allows(membership.getRole(), permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient tenant permission");
        }
    }

    // "What is this person allowed to do in this organization?"
    // Finds their membership, looks up their role, and returns every permission
    // code they have (e.g. ["tenant.read", "documents.read", "members.invite"]).
    // This list is sent to the frontend so it knows which buttons to show.
    public Set<String> getPermissionCodes(Authentication authentication, UUID tenantId) {
        var membership = findActiveMembership(authentication, tenantId);
        return RolePermissions.forRole(membership.getRole()).stream()
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    // The core identity and membership check. Runs every time someone tries to
    // access an organization. Goes through multiple steps:
    private TenantMembership findActiveMembership(
            Authentication authentication,
            UUID tenantId
    ) {
        // Step 1: Are you logged in at all?
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // Step 2: Did you specify which organization you're trying to access?
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Step 3: Look up the user by their email address.
        var user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Step 4: Is this organization still active? (Not deactivated or deleted)
        if (!tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }

        // Step 5: Is this person an active member of this organization?
        var memberships = tenantMembershipRepository.findByUserIdAndTenantIdAndMemberStatus(
                user.getId(),
                tenantId,
                MemberStatus.ACTIVE
        );

        // No membership found — they're not a member of this org.
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        // More than one active membership for the same person in the same org
        // means something went wrong in the data.
        if (memberships.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant membership configuration conflict");
        }

        // Step 6: Final safety check — make sure the membership record truly
        // matches this user, this organization, and is active.
        var membership = memberships.getFirst();
        if (!user.getId().equals(membership.getUserId())
                || !tenantId.equals(membership.getTenantId())
                || membership.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }

        // All checks passed — return the membership so we can look up their role.
        return membership;
    }

    public boolean hasPermission(Authentication authentication, UUID tenantId, Permission permission) {
        var membership = findActiveMembership(authentication, tenantId);
        return permission != null && RolePermissions.allows(membership.getRole(), permission);
    }

    public Roles getMembershipRole(Authentication authentication, UUID tenantId) {
        return findActiveMembership(authentication, tenantId).getRole();
    }

    // Holding a member permission doesn't reach every role: an Admin can't touch another
    // Admin, and a Manager can only invite Members. See RolePermissions.canAssignRole.
    public void requireCanAssignRole(Authentication authentication, UUID tenantId, Roles targetRole) {
        if (!RolePermissions.canAssignRole(getMembershipRole(authentication, tenantId), targetRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't manage this role");
        }
    }
}
