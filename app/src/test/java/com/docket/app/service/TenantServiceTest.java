package com.docket.app.service;

import com.docket.app.dto.CreateTenantRequestDTO;
import com.docket.app.dto.TransferOwnershipRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.UserType;
import com.docket.app.entity.Folder;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        assertEquals(MemberStatus.ACTIVE, membershipCaptor.getValue().getMemberStatus());
    }

    @Test
    void transferOwnershipDemotesOldOwnerBeforePromotingNewOwner() {
        TransferFixture fixture = new TransferFixture();
        fixture.stubUpToTargetLookup();
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndMemberStatus(
                fixture.owner.getId(), fixture.tenant.getId(), MemberStatus.ACTIVE
        )).thenReturn(List.of(fixture.ownerMembership));

        // Record each role at save time, since the same objects are changed in place.
        List<Roles> savedRoles = new ArrayList<>();
        List<TenantMembership> savedMemberships = new ArrayList<>();
        when(tenantMembershipRepository.saveAndFlush(any(TenantMembership.class))).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            savedMemberships.add(membership);
            savedRoles.add(membership.getRole());
            return membership;
        });

        tenantService.transferOwnership(fixture.request("correct-password"), "acme", fixture.authentication);

        assertEquals(List.of(fixture.ownerMembership, fixture.targetMembership), savedMemberships);
        assertEquals(List.of(Roles.ADMIN, Roles.OWNER), savedRoles);
    }

    @Test
    void transferOwnershipRequiresTransferPermission() {
        TransferFixture fixture = new TransferFixture();
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(fixture.tenant));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient tenant permission"))
                .when(tenantAuthorizationService)
                .requirePermission(fixture.authentication, fixture.tenant.getId(), Permission.TENANT_TRANSFER_OWNERSHIP);

        assertTransferFails(fixture, HttpStatus.FORBIDDEN);
        verifyNoInteractions(passwordEncoder, tenantMembershipRepository);
    }

    @Test
    void transferOwnershipRejectsWrongPassword() {
        TransferFixture fixture = new TransferFixture();
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(fixture.tenant));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(fixture.owner));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(false);

        assertTransferFails(fixture, HttpStatus.FORBIDDEN);
        verifyNoInteractions(tenantMembershipRepository);
    }

    @Test
    void transferOwnershipRejectsMemberOfAnotherTenant() {
        TransferFixture fixture = new TransferFixture();
        fixture.targetMembership.setTenantId(UUID.randomUUID());
        fixture.stubUpToTargetLookup();

        assertTransferFails(fixture, HttpStatus.NOT_FOUND);
    }

    @Test
    void transferOwnershipRejectsTargetWhoIsAlreadyOwner() {
        TransferFixture fixture = new TransferFixture();
        fixture.targetMembership.setRole(Roles.OWNER);
        fixture.stubUpToTargetLookup();

        assertTransferFails(fixture, HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferOwnershipRejectsInactiveTarget() {
        TransferFixture fixture = new TransferFixture();
        fixture.targetMembership.setMemberStatus(MemberStatus.INVITED);
        fixture.stubUpToTargetLookup();

        assertTransferFails(fixture, HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferOwnershipRejectsExternalTarget() {
        TransferFixture fixture = new TransferFixture();
        fixture.targetMembership.setUserType(UserType.EXTERNAL);
        fixture.stubUpToTargetLookup();

        assertTransferFails(fixture, HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferOwnershipReturnsConflictWhenAnotherTransferWins() {
        TransferFixture fixture = new TransferFixture();
        fixture.stubUpToTargetLookup();
        when(tenantMembershipRepository.findByUserIdAndTenantIdAndMemberStatus(
                fixture.owner.getId(), fixture.tenant.getId(), MemberStatus.ACTIVE
        )).thenReturn(List.of(fixture.ownerMembership));
        when(tenantMembershipRepository.saveAndFlush(any(TenantMembership.class)))
                .thenThrow(new DataIntegrityViolationException("uk_tenant_membership_single_active_owner"));

        assertTransferFails(fixture, HttpStatus.CONFLICT);
    }

    private void assertTransferFails(TransferFixture fixture, HttpStatus expectedStatus) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tenantService.transferOwnership(
                        fixture.request("correct-password"), "acme", fixture.authentication)
        );

        assertEquals(expectedStatus, exception.getStatusCode());
    }

    // A tenant with an Owner (Ada) and a valid transfer target, ready for each test to adjust.
    private class TransferFixture {
        final Authentication authentication =
                new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());
        final Tenant tenant = new Tenant();
        final User owner = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        final TenantMembership ownerMembership = new TenantMembership();
        final TenantMembership targetMembership = new TenantMembership();

        TransferFixture() {
            tenant.setId(UUID.randomUUID());
            owner.setId(UUID.randomUUID());

            ownerMembership.setTenantMembershipId(UUID.randomUUID());
            ownerMembership.setTenantId(tenant.getId());
            ownerMembership.setUserId(owner.getId());
            ownerMembership.setUserType(UserType.INTERNAL);
            ownerMembership.setMemberStatus(MemberStatus.ACTIVE);
            ownerMembership.setRole(Roles.OWNER);

            targetMembership.setTenantMembershipId(UUID.randomUUID());
            targetMembership.setTenantId(tenant.getId());
            targetMembership.setUserId(UUID.randomUUID());
            targetMembership.setUserType(UserType.INTERNAL);
            targetMembership.setMemberStatus(MemberStatus.ACTIVE);
            targetMembership.setRole(Roles.MEMBER);
        }

        TransferOwnershipRequestDTO request(String password) {
            return new TransferOwnershipRequestDTO(targetMembership.getTenantMembershipId(), password);
        }

        void stubUpToTargetLookup() {
            when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(owner));
            when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);
            when(tenantMembershipRepository.findById(targetMembership.getTenantMembershipId()))
                    .thenReturn(Optional.of(targetMembership));
        }
    }
}
