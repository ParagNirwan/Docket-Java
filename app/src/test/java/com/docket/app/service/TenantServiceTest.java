package com.docket.app.service;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Status;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMembershipRepository tenantMembershipRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void findBySlugRequiresTenantReadPermissionAndReturnsTenant() {
        String slug = "acme";
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());
        Tenant tenant = new Tenant();
        UUID tenantId = UUID.randomUUID();
        tenant.setId(tenantId);

        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));

        Tenant result = tenantService.findBySlug(slug, authentication);

        assertSame(tenant, result);
        verify(tenantAuthorizationService).requirePermission(
                authentication,
                tenantId,
                Permission.TENANT_READ
        );
    }

    @Test
    void findBySlugDoesNotReturnTenantWhenAuthorizationDeniesAccess() {
        String slug = "acme";
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());
        Tenant tenant = new Tenant();
        UUID tenantId = UUID.randomUUID();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient tenant permission"))
                .when(tenantAuthorizationService)
                .requirePermission(authentication, tenantId, Permission.TENANT_READ);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantService.findBySlug(slug, authentication)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void findBySlugDoesNotAuthorizeWhenTenantDoesNotExist() {
        String slug = "missing";
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());
        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantService.findBySlug(slug, authentication)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(tenantAuthorizationService);
    }

    @Test
    void tenantCreationRequiresAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantService.addTenant(new CreateTenantRequestDTO("Acme", "acme"), null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(tenantRepository, userRepository, tenantMembershipRepository);
    }

    @Test
    void authenticatedTenantCreatorBecomesActiveOwner() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "ada@example.com", "credentials", List.of()
        );
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        user.setId(UUID.randomUUID());
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(tenantId);
            return tenant;
        });
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        Tenant created = tenantService.addTenant(
                new CreateTenantRequestDTO("Acme", "acme"),
                authentication
        );

        assertEquals(tenantId, created.getId());
        ArgumentCaptor<TenantMembership> membershipCaptor =
                ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).save(membershipCaptor.capture());
        assertEquals(user.getId(), membershipCaptor.getValue().getUserId());
        assertEquals(tenantId, membershipCaptor.getValue().getTenantId());
        assertEquals(Roles.OWNER, membershipCaptor.getValue().getRole());
        assertEquals(Status.ACTIVE, membershipCaptor.getValue().getStatus());
    }
}
