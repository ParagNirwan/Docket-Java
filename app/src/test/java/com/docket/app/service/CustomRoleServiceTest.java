package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.TenantRole;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.TenantRoleRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomRoleServiceTest {
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private TenantMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private AuditService auditService;

    @InjectMocks
    private CustomRoleService service;

    private final Authentication admin = new UsernamePasswordAuthenticationToken("admin@acme.com", null, List.of());
    private final Tenant tenant = new Tenant();

    @BeforeEach
    void setUp() {
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("acme");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        // An Admin: holds retention.manage, not billing.
        lenient().when(tenantAuthorizationService.getPermissionCodes(admin, tenant.getId()))
                .thenReturn(Set.of("retention.manage", "audit.read", "members.update_role"));
    }

    private CustomRoleService.RoleRequest request(String... permissions) {
        return new CustomRoleService.RoleRequest("Records clerk", null, List.of(permissions));
    }

    @Test
    void createsARoleFromPermissionsTheCreatorHolds() {
        CustomRoleService.RoleDTO role = service.create("acme", request("retention.manage", "audit.read"), admin);

        assertEquals(List.of("retention.manage", "audit.read"), role.permissions());
        verify(tenantRoleRepository).save(any(TenantRole.class));
    }

    @Test
    void cannotGrantPermissionsTheCreatorLacks() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("acme", request("retention.manage", "api.manage"), admin));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(tenantRoleRepository, never()).save(any());
    }

    @Test
    void ownerOnlyPermissionsCanNeverBeGranted() {
        assertThrows(ResponseStatusException.class, () -> service.create("acme", request("billing.manage"), admin));
        assertThrows(ResponseStatusException.class, () -> service.create("acme", request("tenant.transfer_ownership"), admin));
        assertFalse(CustomRoleService.grantableCodes().contains(Permission.BILLING_MANAGE.getCode()));
    }

    @Test
    void rejectsUnknownPermissionsAndBuiltInNames() {
        assertThrows(ResponseStatusException.class, () -> service.create("acme", request("everything"), admin));
        assertThrows(ResponseStatusException.class, () -> service.create("acme",
                new CustomRoleService.RoleRequest("Admin", null, List.of("audit.read")), admin));
    }

    @Test
    void assigningChecksHierarchyAndHeldPermissions() {
        TenantMembership member = new TenantMembership();
        member.setTenantMembershipId(UUID.randomUUID());
        member.setTenantId(tenant.getId());
        member.setRole(Roles.MEMBER);
        TenantRole role = new TenantRole();
        role.setId(UUID.randomUUID());
        role.setPermissions(List.of("api.manage"));
        when(membershipRepository.findById(member.getTenantMembershipId())).thenReturn(Optional.of(member));
        when(tenantRoleRepository.findByIdAndTenantId(role.getId(), tenant.getId())).thenReturn(Optional.of(role));

        assertThrows(ResponseStatusException.class,
                () -> service.assign("acme", member.getTenantMembershipId(), role.getId(), admin));

        verify(tenantAuthorizationService).requireCanAssignRole(admin, tenant.getId(), Roles.MEMBER);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void ownersCantBeGivenACustomRole() {
        TenantMembership owner = new TenantMembership();
        owner.setTenantMembershipId(UUID.randomUUID());
        owner.setTenantId(tenant.getId());
        owner.setRole(Roles.OWNER);
        when(membershipRepository.findById(owner.getTenantMembershipId())).thenReturn(Optional.of(owner));

        assertThrows(ResponseStatusException.class,
                () -> service.assign("acme", owner.getTenantMembershipId(), null, admin));
    }
}
