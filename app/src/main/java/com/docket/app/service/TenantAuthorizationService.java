package com.docket.app.service;

import com.docket.app.entity.ApiKey;
import com.docket.app.entity.ServiceAccount;
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

import java.util.List;
import java.util.Optional;
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
    private final com.docket.app.repository.TenantRoleRepository tenantRoleRepository;

    // "Is this person allowed to perform this action in this organization?"
    // First finds who they are and confirms they're a member, then checks the
    // rulebook to see if their role grants the requested permission.
    // If not, blocks them with a "you don't have permission" error.
    public void requirePermission(
            Authentication authentication,
            UUID tenantId,
            Permission permission
    ) {
        Optional<MachinePrincipal> machine = machinePrincipal(authentication);
        if (machine.isPresent()) {
            if (!machineAllows(machine.get(), tenantId, permission)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient API scope");
            }
            return;
        }
        var membership = findActiveMembership(authentication, tenantId);

        if (!membershipAllows(membership, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient tenant permission");
        }
    }

    // "What is this person allowed to do in this organization?"
    // Finds their membership, looks up their role, and returns every permission
    // code they have (e.g. ["tenant.read", "documents.read", "members.invite"]).
    // This list is sent to the frontend so it knows which buttons to show.
    public Set<String> getPermissionCodes(Authentication authentication, UUID tenantId) {
        Optional<MachinePrincipal> machine = machinePrincipal(authentication);
        if (machine.isPresent()) {
            return java.util.Arrays.stream(Permission.values())
                    .filter(permission -> machineAllows(machine.get(), tenantId, permission))
                    .map(Permission::getCode)
                    .collect(Collectors.toUnmodifiableSet());
        }
        var membership = findActiveMembership(authentication, tenantId);
        Set<String> codes = new java.util.HashSet<>();
        RolePermissions.forRole(membership.getRole()).forEach(permission -> codes.add(permission.getCode()));
        codes.addAll(customRoleCodes(membership));
        return Set.copyOf(codes);
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
        Optional<MachinePrincipal> machine = machinePrincipal(authentication);
        if (machine.isPresent()) {
            return machineAllows(machine.get(), tenantId, permission);
        }
        var membership = findActiveMembership(authentication, tenantId);
        return membershipAllows(membership, permission);
    }

    // API keys and service accounts see what an ordinary member sees (ALL_MEMBERS trays,
    // never admin-only or custom ones), whatever their scopes.
    public Roles getMembershipRole(Authentication authentication, UUID tenantId) {
        Optional<MachinePrincipal> machine = machinePrincipal(authentication);
        if (machine.isPresent()) {
            requireMachineTenant(machine.get(), tenantId);
            return Roles.MEMBER;
        }
        return findActiveMembership(authentication, tenantId).getRole();
    }

    // Built-in role, plus whatever the member's custom role adds. Custom roles can only add,
    // and CustomRoleService keeps owner-only permissions out of them.
    private boolean membershipAllows(TenantMembership membership, Permission permission) {
        if (permission == null) return false;
        return RolePermissions.allows(membership.getRole(), permission)
                || customRoleCodes(membership).contains(permission.getCode());
    }

    private List<String> customRoleCodes(TenantMembership membership) {
        if (membership.getCustomRoleId() == null || tenantRoleRepository == null) {
            return List.of();
        }
        return tenantRoleRepository.findByIdAndTenantId(membership.getCustomRoleId(), membership.getTenantId())
                .map(role -> role.getPermissions() == null ? List.<String>of() : role.getPermissions())
                .orElse(List.of());
    }

    // ─── Machine credentials (API keys, service accounts) ─────────────────
    // They aren't users, so they have no membership. Each one belongs to exactly one tenant
    // and may only use the permissions its scopes grant.
    private record MachinePrincipal(UUID tenantId, List<String> scopes) {
    }

    private static Optional<MachinePrincipal> machinePrincipal(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApiKey key) {
            return Optional.of(new MachinePrincipal(key.getTenantId(), key.getScopes()));
        }
        if (principal instanceof ServiceAccount account) {
            return Optional.of(new MachinePrincipal(account.getTenantId(), account.getScopes()));
        }
        return Optional.empty();
    }

    private boolean machineAllows(MachinePrincipal machine, UUID tenantId, Permission permission) {
        return tenantId != null
                && tenantId.equals(machine.tenantId())
                && tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)
                && ApiScope.grants(machine.scopes(), permission);
    }

    // Not found rather than forbidden: a key shouldn't learn which other tenants exist.
    private void requireMachineTenant(MachinePrincipal machine, UUID tenantId) {
        if (tenantId == null || !tenantId.equals(machine.tenantId())
                || !tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
    }

    // Holding a member permission doesn't reach every role: an Admin can't touch another
    // Admin, and a Manager can only invite Members. See RolePermissions.canAssignRole.
    public void requireCanAssignRole(Authentication authentication, UUID tenantId, Roles targetRole) {
        if (!RolePermissions.canAssignRole(getMembershipRole(authentication, tenantId), targetRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't manage this role");
        }
    }
}
