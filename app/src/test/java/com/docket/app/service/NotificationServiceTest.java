package com.docket.app.service;

import com.docket.app.dto.NotificationPreferencesDTO;
import com.docket.app.entity.Notification;
import com.docket.app.entity.NotificationPreference;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.NotificationPreferenceRepository;
import com.docket.app.repository.NotificationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private TenantMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailOutboxService emailOutboxService;

    @InjectMocks
    private NotificationService service;

    private UUID tenantId;
    private User user;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseUrl", "https://docket.test");
        user = new User("Ada", "ada@example.com", "x");
        user.setId(UUID.randomUUID());
        user.setEmail("ada@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
    }

    private void preferences(boolean inApp, boolean email, boolean digest) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(user.getId());
        preference.setInApp(inApp);
        preference.setEmail(email);
        preference.setDailyDigest(digest);
        when(preferenceRepository.findById(user.getId())).thenReturn(Optional.of(preference));
    }

    private void send() {
        service.notify(tenantId, user.getId(), NotificationType.ROLE_CHANGED, "Role changed", "Now Admin", "/workspace/acme");
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void defaults_showUnderTheBellAndEmail() {
        send();

        assertTrue(saved().isInApp());
        verify(emailOutboxService).enqueue("ada@example.com", EmailTemplate.NOTIFICATION, "Role changed",
                Map.of("message", "Role changed. Now Admin", "link", "https://docket.test/workspace/acme"));
    }

    @Test
    void emailOff_sendsNoEmail() {
        preferences(true, false, false);

        send();

        assertFalse(saved().isDigestPending());
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void everythingOff_keepsNothing() {
        preferences(false, false, false);

        send();

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void digest_replacesTheImmediateEmail() {
        preferences(true, true, true);

        send();

        assertTrue(saved().isDigestPending());
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void digestOnly_isKeptForTheDigestButHiddenFromTheBell() {
        preferences(false, false, true);

        send();

        Notification notification = saved();
        assertFalse(notification.isInApp());
        assertTrue(notification.isDigestPending());
    }

    @Test
    void permissionHolders_reachesOnlyThoseRoles() {
        TenantMembership owner = membership(user.getId(), Roles.OWNER);
        TenantMembership member = membership(UUID.randomUUID(), Roles.MEMBER);
        when(membershipRepository.findByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE)).thenReturn(List.of(owner, member));

        service.notifyPermissionHolders(tenantId, Permission.RETENTION_READ, NotificationType.RETENTION_EXPIRED, "t", null, "/x");

        assertEquals(user.getId(), saved().getUserId());
    }

    private TenantMembership membership(UUID userId, Roles role) {
        TenantMembership membership = new TenantMembership();
        membership.setUserId(userId);
        membership.setRole(role);
        return membership;
    }

    @Test
    void sendDigest_oneEmailThenNothingPending() {
        Notification first = new Notification();
        first.setTitle("First");
        Notification second = new Notification();
        second.setTitle("Second");
        when(notificationRepository.findByUserIdAndDigestPendingIsTrueOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(first, second));

        service.sendDigest(user.getId());

        verify(notificationRepository).clearPendingDigest(user.getId());
        verify(emailOutboxService).enqueue(eq("ada@example.com"), eq(EmailTemplate.NOTIFICATION), eq("2 new notifications from Docket"),
                eq(Map.of("message", "First; Second.", "link", "https://docket.test/tenants")));
    }

    @Test
    void sendDigest_nothingWaitingSendsNothing() {
        service.sendDigest(user.getId());

        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void markRead_someoneElsesLooksMissing() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.markRead(UUID.randomUUID(), authentication));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void updatePreferences_createsARowForSomeoneOnTheDefaults() {
        service.updatePreferences(new NotificationPreferencesDTO(false, false, true), authentication);

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(captor.capture());
        assertEquals(user.getId(), captor.getValue().getUserId());
        assertTrue(captor.getValue().isDailyDigest());
    }

    @Test
    void workspaceLink_encodesTheSlug() {
        assertEquals("/workspace/a%20b?section=settings&tab=retention", NotificationService.workspaceLink("a b", "settings", "retention"));
    }
}
