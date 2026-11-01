package com.docket.app.service;

import com.docket.app.dto.MemberDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.RefreshTokenRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private SessionService sessionService;
    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MemberService service;

    private UUID tenantId;
    private User actor;
    private TenantMembership target;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        actor = new User("Ada Lovelace", "ada@example.com", "encoded");
        actor.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(actor));

        target = membership(UUID.randomUUID(), Roles.MEMBER);
        when(membershipRepository.findById(target.getTenantMembershipId())).thenReturn(Optional.of(target));
    }

    private TenantMembership membership(UUID userId, Roles role) {
        TenantMembership membership = new TenantMembership();
        membership.setTenantMembershipId(UUID.randomUUID());
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setMemberStatus(MemberStatus.ACTIVE);
        return membership;
    }

    private void actorCannotManage(Roles role) {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requireCanAssignRole(authentication, tenantId, role);
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    @Test
    void list_includesLastActiveAndMarksTheCurrentUser() {
        User other = new User("Bob", "bob@x.com", "encoded");
        other.setId(UUID.randomUUID());
        Instant lastSeen = Instant.parse("2026-10-01T10:00:00Z");
        when(membershipRepository.findByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE))
                .thenReturn(List.of(membership(actor.getId(), Roles.OWNER), membership(other.getId(), Roles.MEMBER)));
        when(userRepository.findAllById(any())).thenReturn(List.of(actor, other));
        when(refreshTokenRepository.findLastIssuedByUserIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{other.getId(), lastSeen}));

        List<MemberDTO> members = service.list(SLUG, authentication);

        assertEquals(List.of("Ada Lovelace", "Bob"), members.stream().map(MemberDTO::name).toList());
        assertTrue(members.get(0).currentUser());
        assertFalse(members.get(1).currentUser());
        assertEquals(lastSeen, members.get(1).lastActiveAt());
    }

    @Test
    void list_requiresMembersRead() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.MEMBERS_READ);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.list(SLUG, authentication));
    }

    @Test
    void updateRole_savesAuditsAndInvalidatesTheirTokens() {
        service.updateRole(SLUG, target.getTenantMembershipId(), Roles.MANAGER, authentication);

        assertEquals(Roles.MANAGER, target.getRole());
        verify(membershipRepository).save(target);
        verify(auditService).record(eq(tenantId), eq(actor.getId()), eq(AuditAction.MEMBER_ROLE_CHANGED), any(), any(), any());
        verify(sessionService).invalidateAccessTokens(target.getUserId());
    }

    @Test
    void updateRole_ownRole_isForbidden() {
        target.setUserId(actor.getId());

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateRole(SLUG, target.getTenantMembershipId(), Roles.ADMIN, authentication));
        verify(membershipRepository, never()).save(any());
    }

    // An Admin may assign Member, but that mustn't let them demote another Admin to it.
    @Test
    void updateRole_memberAboveTheActor_isForbiddenEvenForAnAllowedNewRole() {
        target.setRole(Roles.ADMIN);
        actorCannotManage(Roles.ADMIN);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateRole(SLUG, target.getTenantMembershipId(), Roles.MEMBER, authentication));
        assertEquals(Roles.ADMIN, target.getRole());
    }

    @Test
    void updateRole_toARoleAboveTheActor_isForbidden() {
        actorCannotManage(Roles.ADMIN);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateRole(SLUG, target.getTenantMembershipId(), Roles.ADMIN, authentication));
        assertEquals(Roles.MEMBER, target.getRole());
    }

    @Test
    void updateRole_memberOfAnotherTenant_isNotFound() {
        target.setTenantId(UUID.randomUUID());

        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.updateRole(SLUG, target.getTenantMembershipId(), Roles.MANAGER, authentication));
    }

    @Test
    void remove_endsTheMembershipAndInvalidatesTheirTokens() {
        service.remove(SLUG, target.getTenantMembershipId(), authentication);

        assertEquals(MemberStatus.DELETED, target.getMemberStatus());
        verify(auditService).record(eq(tenantId), eq(actor.getId()), eq(AuditAction.MEMBER_REMOVED), any(), any(), any());
        verify(sessionService).invalidateAccessTokens(target.getUserId());
    }

    @Test
    void remove_self_isRejected() {
        target.setUserId(actor.getId());

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.remove(SLUG, target.getTenantMembershipId(), authentication));
        assertEquals(MemberStatus.ACTIVE, target.getMemberStatus());
    }

    @Test
    void remove_theOwner_isForbidden() {
        target.setRole(Roles.OWNER);
        actorCannotManage(Roles.OWNER);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.remove(SLUG, target.getTenantMembershipId(), authentication));
        assertEquals(MemberStatus.ACTIVE, target.getMemberStatus());
    }

    @Test
    void leave_endsTheirOwnMembership() {
        TenantMembership own = membership(actor.getId(), Roles.MANAGER);
        when(membershipRepository.findByUserIdAndTenantIdAndMemberStatus(actor.getId(), tenantId, MemberStatus.ACTIVE))
                .thenReturn(List.of(own));

        service.leave(SLUG, authentication);

        assertEquals(MemberStatus.INACTIVE, own.getMemberStatus());
        verify(auditService).record(eq(tenantId), eq(actor.getId()), eq(AuditAction.MEMBER_LEFT), any(), any(), any());
    }

    @Test
    void leave_asOwner_conflicts() {
        TenantMembership own = membership(actor.getId(), Roles.OWNER);
        when(membershipRepository.findByUserIdAndTenantIdAndMemberStatus(actor.getId(), tenantId, MemberStatus.ACTIVE))
                .thenReturn(List.of(own));

        assertStatus(HttpStatus.CONFLICT, () -> service.leave(SLUG, authentication));
        assertEquals(MemberStatus.ACTIVE, own.getMemberStatus());
    }
}
