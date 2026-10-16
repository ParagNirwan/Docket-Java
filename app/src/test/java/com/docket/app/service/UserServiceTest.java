package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Status;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMembershipRepository tenantMembershipRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void listTenantsReturnsOnlyActiveTenantsForUsersActiveMemberships() {
        String email = "ada@example.com";
        User user = new User("Ada Lovelace", email, "encoded-password");
        UUID activeTenantId = UUID.randomUUID();
        UUID inactiveTenantId = UUID.randomUUID();
        user.setId(UUID.randomUUID());

        TenantMembership activeMembership = membership(user.getId(), activeTenantId, Status.ACTIVE);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(tenantMembershipRepository.findByUserIdAndStatus(user.getId(), Status.ACTIVE))
                .thenReturn(List.of(activeMembership));

        Tenant activeTenant = tenant(activeTenantId, TenantStatus.ACTIVE);
        Tenant inactiveTenant = tenant(inactiveTenantId, TenantStatus.INACTIVE);
        when(tenantRepository.findAllById(List.of(activeTenantId)))
                .thenReturn(List.of(activeTenant, inactiveTenant));

        assertEquals(List.of(activeTenant), userService.listTenantsForUser(email));
        verify(tenantMembershipRepository).findByUserIdAndStatus(user.getId(), Status.ACTIVE);
    }

    @Test
    void listTenantsRejectsUnknownUser() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.listTenantsForUser("missing@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private TenantMembership membership(UUID userId, UUID tenantId, Status status) {
        TenantMembership membership = new TenantMembership();
        membership.setUserId(userId);
        membership.setTenantId(tenantId);
        membership.setRole(Roles.OWNER);
        membership.setStatus(status);
        return membership;
    }

    private Tenant tenant(UUID id, TenantStatus status) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setStatus(status);
        return tenant;
    }
}
