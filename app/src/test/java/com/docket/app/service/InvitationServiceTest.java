package com.docket.app.service;

import com.docket.app.dto.InviteMemberRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantInvitation;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.InvitationStatus;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.TenantStatus;
import com.docket.app.enums.UserType;
import com.docket.app.repository.TenantInvitationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantInvitationRepository invitationRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private AuthRateLimiter authRateLimiter;
    @Mock
    private EmailOutboxService emailOutboxService;
    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InvitationService service;

    private Tenant tenant;
    private User inviter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "https://docket.test");
        ReflectionTestUtils.setField(service, "ttlDays", 7);
        ReflectionTestUtils.setField(service, "limitPerHour", 50);

        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Acme");
        tenant.setSlug(SLUG);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        inviter = user("Ada Lovelace", "ada@example.com");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(inviter));
        when(invitationRepository.markAccepted(any(), any())).thenReturn(1);
    }

    private User user(String name, String email) {
        User user = new User(name, email, "encoded");
        user.setId(UUID.randomUUID());
        return user;
    }

    private TenantInvitation invitation(String rawToken, String email, InvitationStatus status, Instant expiresAt) {
        TenantInvitation invitation = new TenantInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setTenantId(tenant.getId());
        invitation.setEmail(email);
        invitation.setRole(Roles.MANAGER);
        invitation.setStatus(status);
        invitation.setExpiresAt(expiresAt);
        invitation.setTokenHash(TokenHasher.hash(rawToken));
        when(invitationRepository.findByTokenHash(TokenHasher.hash(rawToken))).thenReturn(Optional.of(invitation));
        return invitation;
    }

    private TenantInvitation liveInvitation(String rawToken, String email) {
        return invitation(rawToken, email, InvitationStatus.PENDING, Instant.now().plus(Duration.ofDays(1)));
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    @Test
    void invite_storesOnlyTheHashAndEmailsTheRawToken() {
        service.invite(SLUG, new InviteMemberRequestDTO("Bob@X.com ", Roles.MANAGER), authentication);

        ArgumentCaptor<TenantInvitation> saved = ArgumentCaptor.forClass(TenantInvitation.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        assertEquals("bob@x.com", saved.getValue().getEmail());
        assertEquals(InvitationStatus.PENDING, saved.getValue().getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailOutboxService).enqueue(eq("bob@x.com"), eq(EmailTemplate.INVITE), variables.capture());
        String rawToken = variables.getValue().get("link").toString().split("token=")[1];
        assertNotEquals(rawToken, saved.getValue().getTokenHash());
        assertEquals(TokenHasher.hash(rawToken), saved.getValue().getTokenHash());
        verify(auditService).record(eq(tenant.getId()), eq(inviter.getId()), eq(AuditAction.MEMBER_INVITED), any(), any(), any());
    }

    @Test
    void invite_expiresInSevenDays() {
        service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication);

        ArgumentCaptor<TenantInvitation> saved = ArgumentCaptor.forClass(TenantInvitation.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        Duration ttl = Duration.between(Instant.now(), saved.getValue().getExpiresAt());
        assertTrue(ttl.toDays() == 6 || ttl.toDays() == 7);
    }

    @Test
    void invite_roleTheActorCannotAssign_isForbiddenAndSendsNothing() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requireCanAssignRole(authentication, tenant.getId(), Roles.ADMIN);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.ADMIN), authentication));

        verify(invitationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void invite_existingMember_conflicts() {
        User bob = user("Bob", "bob@x.com");
        when(userRepository.findByEmailIgnoreCase("bob@x.com")).thenReturn(Optional.of(bob));
        when(membershipRepository.findByUserIdAndTenantIdAndMemberStatus(bob.getId(), tenant.getId(), MemberStatus.ACTIVE))
                .thenReturn(List.of(new TenantMembership()));

        assertStatus(HttpStatus.CONFLICT,
                () -> service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication));
    }

    @Test
    void invite_livePendingInvite_conflicts() {
        TenantInvitation existing = liveInvitation("old", "bob@x.com");
        when(invitationRepository.findByTenantIdAndEmailIgnoreCaseAndStatus(tenant.getId(), "bob@x.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(existing));

        assertStatus(HttpStatus.CONFLICT,
                () -> service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication));
    }

    @Test
    void invite_expiredPendingInvite_isRetiredAndReplaced() {
        TenantInvitation expired = invitation("old", "bob@x.com", InvitationStatus.PENDING, Instant.now().minusSeconds(60));
        when(invitationRepository.findByTenantIdAndEmailIgnoreCaseAndStatus(tenant.getId(), "bob@x.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(expired));

        service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication);

        assertEquals(InvitationStatus.EXPIRED, expired.getStatus());
        verify(emailOutboxService).enqueue(eq("bob@x.com"), eq(EmailTemplate.INVITE), any());
    }

    @Test
    void invite_losingARaceToTheUniqueIndex_conflicts() {
        when(invitationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertStatus(HttpStatus.CONFLICT,
                () -> service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication));
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void invite_overTheLimit_isRejected() {
        doThrow(new RateLimitExceededException(60)).when(authRateLimiter).check(any(), eq(50), any());

        assertStatus(HttpStatus.TOO_MANY_REQUESTS,
                () -> service.invite(SLUG, new InviteMemberRequestDTO("bob@x.com", Roles.MEMBER), authentication));
    }

    @Test
    void resend_replacesTheToken() {
        TenantInvitation invitation = liveInvitation("old", "bob@x.com");
        String oldHash = invitation.getTokenHash();
        when(invitationRepository.findByIdAndTenantIdAndStatus(invitation.getId(), tenant.getId(), InvitationStatus.PENDING))
                .thenReturn(Optional.of(invitation));

        service.resend(SLUG, invitation.getId(), authentication);

        assertNotEquals(oldHash, invitation.getTokenHash());
        verify(emailOutboxService).enqueue(eq("bob@x.com"), eq(EmailTemplate.INVITE), any());
    }

    @Test
    void revoke_inviteAboveTheActorsReach_isForbidden() {
        TenantInvitation invitation = liveInvitation("token", "bob@x.com");
        when(invitationRepository.findByIdAndTenantIdAndStatus(invitation.getId(), tenant.getId(), InvitationStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requireCanAssignRole(authentication, tenant.getId(), Roles.MANAGER);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.revoke(SLUG, invitation.getId(), authentication));
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
    }

    @Test
    void preview_expiredRevokedAndUnknownTokens_allLookTheSame() {
        invitation("expired", "bob@x.com", InvitationStatus.PENDING, Instant.now().minusSeconds(60));
        invitation("revoked", "bob@x.com", InvitationStatus.REVOKED, Instant.now().plus(Duration.ofDays(1)));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.preview("expired"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.preview("revoked"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.preview("unknown"));
    }

    @Test
    void preview_saysWhetherTheAddressHasAnAccount() {
        TenantInvitation invitation = liveInvitation("token", "bob@x.com");
        invitation.setInvitedBy(inviter.getId());
        when(userRepository.findById(inviter.getId())).thenReturn(Optional.of(inviter));
        when(userRepository.existsByEmailIgnoreCase("bob@x.com")).thenReturn(true);

        var preview = service.preview("token");

        assertEquals("Acme", preview.tenantName());
        assertEquals("Ada Lovelace", preview.inviterName());
        assertTrue(preview.hasAccount());
    }

    @Test
    void accept_someoneElsesInvite_isForbidden() {
        liveInvitation("token", "bob@x.com");

        assertStatus(HttpStatus.FORBIDDEN, () -> service.accept("token", authentication));
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_createsAnActiveMembershipWithTheInvitedRole() {
        liveInvitation("token", "ada@example.com");

        var accepted = service.accept("token", authentication);

        ArgumentCaptor<TenantMembership> saved = ArgumentCaptor.forClass(TenantMembership.class);
        verify(membershipRepository).saveAndFlush(saved.capture());
        assertEquals(Roles.MANAGER, saved.getValue().getRole());
        assertEquals(MemberStatus.ACTIVE, saved.getValue().getMemberStatus());
        assertEquals(UserType.INTERNAL, saved.getValue().getUserType());
        assertNotNull(saved.getValue().getJoinedAt());
        assertEquals(SLUG, accepted.tenantSlug());
    }

    @Test
    void accept_someoneWhoLeft_getsTheirOldRecordBack() {
        liveInvitation("token", "ada@example.com");
        TenantMembership old = new TenantMembership();
        old.setMemberStatus(MemberStatus.DELETED);
        old.setRole(Roles.ADMIN);
        when(membershipRepository.findByUserIdAndTenantId(inviter.getId(), tenant.getId())).thenReturn(List.of(old));

        service.accept("token", authentication);

        verify(membershipRepository).saveAndFlush(old);
        assertEquals(MemberStatus.ACTIVE, old.getMemberStatus());
        assertEquals(Roles.MANAGER, old.getRole());
    }

    @Test
    void accept_provesTheEmailAddress() {
        liveInvitation("token", "ada@example.com");

        service.accept("token", authentication);

        assertNotNull(inviter.getEmailVerifiedAt());
    }

    @Test
    void accept_losingTheRaceToClaimTheInvite_addsNobody() {
        liveInvitation("token", "ada@example.com");
        when(invitationRepository.markAccepted(any(), any())).thenReturn(0);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.accept("token", authentication));
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptForNewUser_mismatchedAddress_joinsNothing() {
        liveInvitation("token", "bob@x.com");

        assertFalse(service.acceptForNewUser("token", user("Mallory", "mallory@x.com")));
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void listPending_requiresMembersRead() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(tenantAuthorizationService)
                .requirePermission(authentication, tenant.getId(), Permission.MEMBERS_READ);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.listPending(SLUG, authentication));
        verifyNoInteractions(invitationRepository);
    }
}
