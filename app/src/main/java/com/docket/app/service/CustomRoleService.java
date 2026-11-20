package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.TenantRole;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.TenantRoleRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Organization-defined roles that add permissions on top of a member's built-in role.
// Two rules stop them being used to escalate:
//   1. you can only put permissions into a role that you hold yourself, and
//   2. owner-only permissions (ownership transfer, billing) never go into one.
// Tray visibility still follows the built-in role.
@Service
@RequiredArgsConstructor
public class CustomRoleService {
    public static final Set<Permission> NEVER_GRANTABLE = Set.of(
            Permission.TENANT_TRANSFER_OWNERSHIP, Permission.BILLING_MANAGE);
    private static final int MAX_ROLES = 50;

    private final TenantRepository tenantRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    public record RoleDTO(UUID id, String name, String description, List<String> permissions, long memberCount) {
    }

    public record RoleRequest(String name, String description, List<String> permissions) {
    }

    // The codes a custom role may contain, for the editor's checklist.
    public static List<String> grantableCodes() {
        return Arrays.stream(Permission.values())
                .filter(p -> !NEVER_GRANTABLE.contains(p))
                .map(Permission::getCode)
                .toList();
    }

    public List<RoleDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_READ);
        return tenantRoleRepository.findByTenantIdOrderByNameAsc(tenant.getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public RoleDTO create(String slug, RoleRequest request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_UPDATE_ROLE);
        String name = validName(request.name());
        if (tenantRoleRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A role with this name already exists");
        }
        if (tenantRoleRepository.findByTenantIdOrderByNameAsc(tenant.getId()).size() >= MAX_ROLES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An organization can have at most " + MAX_ROLES + " custom roles");
        }

        TenantRole role = new TenantRole();
        role.setTenantId(tenant.getId());
        role.setName(name);
        role.setDescription(trimToNull(request.description()));
        role.setPermissions(validPermissions(request.permissions(), authentication, tenant.getId()));
        role.setCreatedBy(userRepository.findByEmailIgnoreCase(authentication.getName()).map(u -> u.getId()).orElse(null));
        tenantRoleRepository.save(role);

        auditService.record(authentication, tenant.getId(), AuditAction.MEMBER_ROLE_CHANGED, "TENANT_ROLE", role.getId(),
                Map.of("event", "custom_role_created", "name", name, "permissions", String.join(",", role.getPermissions())));
        return toDto(role);
    }

    @Transactional
    public RoleDTO update(String slug, UUID roleId, RoleRequest request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_UPDATE_ROLE);
        TenantRole role = findRole(tenant.getId(), roleId);

        String name = validName(request.name());
        if (!name.equalsIgnoreCase(role.getName())
                && tenantRoleRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A role with this name already exists");
        }
        role.setName(name);
        role.setDescription(trimToNull(request.description()));
        role.setPermissions(validPermissions(request.permissions(), authentication, tenant.getId()));
        tenantRoleRepository.save(role);

        auditService.record(authentication, tenant.getId(), AuditAction.MEMBER_ROLE_CHANGED, "TENANT_ROLE", role.getId(),
                Map.of("event", "custom_role_updated", "name", name, "permissions", String.join(",", role.getPermissions())));
        return toDto(role);
    }

    // Members holding it simply fall back to their built-in role (ON DELETE SET NULL).
    @Transactional
    public void delete(String slug, UUID roleId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_UPDATE_ROLE);
        TenantRole role = findRole(tenant.getId(), roleId);
        tenantRoleRepository.delete(role);
        auditService.record(authentication, tenant.getId(), AuditAction.MEMBER_ROLE_CHANGED, "TENANT_ROLE", roleId,
                Map.of("event", "custom_role_deleted", "name", role.getName()));
    }

    // roleId null removes the custom role.
    @Transactional
    public void assign(String slug, UUID membershipId, UUID roleId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_UPDATE_ROLE);
        TenantMembership membership = membershipRepository.findById(membershipId)
                .filter(m -> tenant.getId().equals(m.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (membership.getRole() == Roles.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Owner already has every permission");
        }
        // Same hierarchy as changing someone's built-in role: an Admin can't manage another Admin.
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), membership.getRole());

        if (roleId != null) {
            TenantRole role = findRole(tenant.getId(), roleId);
            // Re-checked at assignment: the role may hold permissions this person lacks.
            requireHeldByActor(role.getPermissions(), authentication, tenant.getId());
        }
        membership.setCustomRoleId(roleId);
        membershipRepository.save(membership);
        auditService.record(authentication, tenant.getId(), AuditAction.MEMBER_ROLE_CHANGED, "MEMBERSHIP", membershipId,
                Map.of("event", "custom_role_assigned", "customRoleId", String.valueOf(roleId)));
    }

    private List<String> validPermissions(Collection<String> requested, Authentication authentication, UUID tenantId) {
        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one permission");
        }
        Map<String, Permission> byCode = Arrays.stream(Permission.values())
                .collect(Collectors.toMap(Permission::getCode, p -> p));
        List<String> codes = requested.stream().map(String::trim).distinct().toList();
        for (String code : codes) {
            Permission permission = byCode.get(code);
            if (permission == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission: " + code);
            }
            if (NEVER_GRANTABLE.contains(permission)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code + " is reserved for the Owner");
            }
        }
        requireHeldByActor(codes, authentication, tenantId);
        return codes;
    }

    private void requireHeldByActor(Collection<String> codes, Authentication authentication, UUID tenantId) {
        Set<String> held = tenantAuthorizationService.getPermissionCodes(authentication, tenantId);
        List<String> missing = codes.stream().filter(code -> !held.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can't grant permissions you don't have: " + String.join(", ", missing));
        }
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name must be 1–60 characters");
        }
        boolean clashesWithBuiltIn = Arrays.stream(Roles.values()).anyMatch(r -> r.name().equalsIgnoreCase(trimmed));
        if (clashesWithBuiltIn) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That name is used by a built-in role");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TenantRole findRole(UUID tenantId, UUID roleId) {
        return tenantRoleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private RoleDTO toDto(TenantRole role) {
        return new RoleDTO(role.getId(), role.getName(), role.getDescription(),
                role.getPermissions() == null ? List.of() : role.getPermissions(),
                tenantRoleRepository.countActiveMembers(role.getId()));
    }
}
