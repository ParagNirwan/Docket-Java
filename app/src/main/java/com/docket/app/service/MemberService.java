package com.docket.app.service;

import com.docket.app.dto.MemberDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.RefreshTokenRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Who is in an organization, and changing that. A role change or removal also
// invalidates the person's access tokens, so it applies to the next thing they do.
@Service
@RequiredArgsConstructor
public class MemberService {
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public List<MemberDTO> list(String slug, Authentication authentication) {
        UUID tenantId = findTenant(slug).getId();
        tenantAuthorizationService.requirePermission(authentication, tenantId, Permission.MEMBERS_READ);

        List<TenantMembership> memberships = membershipRepository.findByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE);
        Set<UUID> userIds = memberships.stream().map(TenantMembership::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<UUID, Instant> lastActive = refreshTokenRepository.findLastIssuedByUserIds(userIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Instant) row[1]));

        return memberships.stream()
                .filter(membership -> users.containsKey(membership.getUserId()))
                .map(membership -> {
                    User user = users.get(membership.getUserId());
                    return new MemberDTO(
                            membership.getTenantMembershipId(),
                            user.getName(),
                            user.getEmail(),
                            membership.getRole(),
                            membership.getMemberStatus(),
                            membership.getJoinedAt(),
                            lastActive.get(user.getId()),
                            user.getEmail().equalsIgnoreCase(authentication.getName()));
                })
                .sorted(Comparator.comparing(MemberDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // canAssignRole is checked for both the current and the new role: being allowed to
    // grant Member doesn't mean being allowed to demote an Admin to it.
    @Transactional
    public void updateRole(String slug, UUID membershipId, Roles newRole, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_UPDATE_ROLE);

        TenantMembership member = findActiveMember(tenant, membershipId);
        User actor = currentUser(authentication);
        if (actor.getId().equals(member.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't change your own role");
        }
        Roles previousRole = member.getRole();
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), previousRole);
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), newRole);
        if (previousRole == newRole) {
            return;
        }

        member.setRole(newRole);
        membershipRepository.save(member);
        auditService.record(tenant.getId(), actor.getId(), AuditAction.MEMBER_ROLE_CHANGED, "USER", member.getUserId(),
                Map.of("from", previousRole.name(), "to", newRole.name()));
        notificationService.notify(tenant.getId(), member.getUserId(), NotificationType.ROLE_CHANGED,
                "Your role in " + tenant.getName() + " changed",
                "You are now " + newRole.name().charAt(0) + newRole.name().substring(1).toLowerCase() + ".",
                NotificationService.workspaceLink(tenant.getSlug(), "review", null));
        sessionService.invalidateAccessTokens(member.getUserId());
    }

    @Transactional
    public void remove(String slug, UUID membershipId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_REMOVE);

        TenantMembership member = findActiveMember(tenant, membershipId);
        User actor = currentUser(authentication);
        if (actor.getId().equals(member.getUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use leave to remove yourself");
        }
        // This also protects the Owner, which no role can assign.
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), member.getRole());

        member.setMemberStatus(MemberStatus.DELETED);
        membershipRepository.save(member);
        auditService.record(tenant.getId(), actor.getId(), AuditAction.MEMBER_REMOVED, "USER", member.getUserId(),
                Map.of("role", member.getRole().name()));
        sessionService.invalidateAccessTokens(member.getUserId());
    }

    @Transactional
    public void leave(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_READ);

        User actor = currentUser(authentication);
        TenantMembership membership = membershipRepository
                .findByUserIdAndTenantIdAndMemberStatus(actor.getId(), tenant.getId(), MemberStatus.ACTIVE)
                .getFirst();
        if (membership.getRole() == Roles.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer ownership before leaving");
        }

        membership.setMemberStatus(MemberStatus.INACTIVE);
        membershipRepository.save(membership);
        auditService.record(tenant.getId(), actor.getId(), AuditAction.MEMBER_LEFT, "USER", actor.getId(),
                Map.of("role", membership.getRole().name()));
        sessionService.invalidateAccessTokens(actor.getId());
    }

    private TenantMembership findActiveMember(Tenant tenant, UUID membershipId) {
        return membershipRepository.findById(membershipId)
                .filter(membership -> tenant.getId().equals(membership.getTenantId()))
                .filter(membership -> membership.getMemberStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
