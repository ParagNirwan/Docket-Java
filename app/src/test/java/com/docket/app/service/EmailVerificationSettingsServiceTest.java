package com.docket.app.service;

import com.docket.app.dto.EmailVerificationSettingsDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationSettingsServiceTest {
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMembershipRepository tenantMembershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private AccountRecoveryService accountRecoveryService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private EmailVerificationSettingsService service;

    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            "admin@example.com", "credentials", List.of());

    @Test
    void emailVerificationIsOffForNewAndExistingTenantsByDefault() {
        Tenant tenant = tenant();
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        assertFalse(service.get("acme", authentication).enabled());
        assertFalse(tenant.isEmailVerificationEnabled());
    }

    @Test
    void enablingSendsEmailOnlyToUnverifiedActiveMembers() {
        Tenant tenant = tenant();
        User unverified = user(false);
        User verified = user(true);
        TenantMembership first = membership(tenant.getId(), unverified.getId());
        TenantMembership second = membership(tenant.getId(), verified.getId());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(tenantMembershipRepository.findByTenantIdAndMemberStatus(tenant.getId(), MemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(userRepository.findAllById(Set.of(unverified.getId(), verified.getId())))
                .thenReturn(List.of(unverified, verified));

        EmailVerificationSettingsDTO result =
                service.update("acme", new EmailVerificationSettingsDTO(true), authentication);

        assertTrue(result.enabled());
        assertTrue(tenant.isEmailVerificationEnabled());
        verify(accountRecoveryService).sendVerificationEmail(unverified);
        verify(accountRecoveryService, never()).sendVerificationEmail(verified);
    }

    @Test
    void updatingRequiresTenantUpdatePermission() {
        Tenant tenant = tenant();
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService)
                .requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update("acme", new EmailVerificationSettingsDTO(true), authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(accountRecoveryService, never()).sendVerificationEmail(any());
    }

    @Test
    void reminderIsEnabledWhenAnyActiveTenantOptsIn() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantMembership membership = membership(tenantId, userId);
        Tenant tenant = tenant();
        tenant.setId(tenantId);
        tenant.setEmailVerificationEnabled(true);
        User user = user(false);
        user.setId(userId);
        when(userRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(user));
        when(tenantMembershipRepository.findByUserIdAndMemberStatus(userId, MemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(tenantRepository.findAllById(Set.of(tenantId))).thenReturn(List.of(tenant));

        assertTrue(service.isEnabledForUser("member@example.com"));
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        return tenant;
    }

    private User user(boolean verified) {
        User user = new User("Member User", "member@example.com", "encoded");
        user.setId(UUID.randomUUID());
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return user;
    }

    private TenantMembership membership(UUID tenantId, UUID userId) {
        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setMemberStatus(MemberStatus.ACTIVE);
        return membership;
    }
}
