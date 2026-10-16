package com.docket.app.service;

import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Status;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthorizationServiceTest {
    private static final String EMAIL = "ada@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMembershipRepository tenantMembershipRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantAuthorizationService tenantAuthorizationService;

    private UUID userId;
    private UUID tenantId;
    private UsernamePasswordAuthenticationToken authentication;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(EMAIL, "credentials", List.of());
        user = new User("Ada Lovelace", EMAIL, "encoded-password");
        user.setId(userId);

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)).thenReturn(true);
    }

    @Test
    void allowsPermissionGrantedToActiveMembershipRole() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(membership(tenantId, Roles.MEMBER, Status.ACTIVE)));

        tenantAuthorizationService.requirePermission(
                authentication, tenantId, Permission.DOCUMENTS_READ
        );
    }

    @Test
    void returnsPermissionCodesFromActiveTenantRole() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(membership(tenantId, Roles.MEMBER, Status.ACTIVE)));

        Set<String> permissions = tenantAuthorizationService.getPermissionCodes(authentication, tenantId);

        assertEquals(Set.of("tenant.read", "documents.read"), permissions);
    }

    @Test
    void deniesPermissionNotGrantedToMembershipRole() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(membership(tenantId, Roles.MEMBER, Status.ACTIVE)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_UPDATE
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void deniesWhenUserHasNoMembershipInTenant() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void deniesWhenTenantIsInactive() {
        when(tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void doesNotAcceptInactiveMembershipAsActive() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(tenantMembershipRepository).findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        );
    }

    @Test
    void deniesMembershipForDifferentTenant() {
        UUID otherTenantId = UUID.randomUUID();
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(membership(otherTenantId, Roles.OWNER, Status.ACTIVE)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void deniesMembershipWithUnknownRole() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(membership(tenantId, null, Status.ACTIVE)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(Set.of(), tenantAuthorizationService.getPermissionCodes(authentication, tenantId));
    }

    @Test
    void deniesAmbiguousDuplicateActiveMemberships() {
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndStatus(
                userId, tenantId, Status.ACTIVE
        )).thenReturn(List.of(
                membership(tenantId, Roles.OWNER, Status.ACTIVE),
                membership(tenantId, Roles.MEMBER, Status.ACTIVE)
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantAuthorizationService.requirePermission(
                        authentication, tenantId, Permission.TENANT_READ
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private TenantMembership membership(UUID membershipTenantId, Roles role, Status status) {
        TenantMembership membership = new TenantMembership();
        membership.setUserId(userId);
        membership.setTenantId(membershipTenantId);
        membership.setRole(role);
        membership.setStatus(status);
        return membership;
    }
}
