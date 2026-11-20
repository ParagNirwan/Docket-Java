package com.docket.app.service;

import com.docket.app.dto.TenantDomainDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantDomain;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.DomainJoinMode;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantDomainRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.service.domain.DomainTxtLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDomainServiceTest {
    @Mock private TenantDomainRepository tenantDomainRepository;
    @Mock private DomainTxtLookup domainTxtLookup;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantMembershipRepository membershipRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private AuditService auditService;

    @InjectMocks
    private TenantDomainService service;

    private final Authentication auth = mock(Authentication.class);
    private final Tenant tenant = new Tenant();
    private TenantDomain pending;

    @BeforeEach
    void setUp() {
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);

        pending = new TenantDomain();
        pending.setId(UUID.randomUUID());
        pending.setTenantId(tenant.getId());
        pending.setDomain("acme.com");
        pending.setVerificationToken("tok123");
        pending.setJoinMode(DomainJoinMode.AUTO);
    }

    private void tenantExists() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
    }

    @Test
    void verifyFailsWithoutTheTxtRecordEvenThoughTheTokenIsKnown() {
        tenantExists();
        when(tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), "acme.com"))
                .thenReturn(Optional.of(pending));
        when(domainTxtLookup.txtRecords("_docket-verification.acme.com")).thenReturn(List.of("unrelated"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verify("acme", "acme.com", auth));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(null, pending.getVerifiedAt());
        verify(tenantDomainRepository, never()).save(any());
    }

    @Test
    void verifySucceedsWhenTheTxtRecordIsPublished() {
        tenantExists();
        when(tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), "acme.com"))
                .thenReturn(Optional.of(pending));
        when(domainTxtLookup.txtRecords("_docket-verification.acme.com"))
                .thenReturn(List.of("v=spf1 -all", "docket-verification=tok123"));

        TenantDomainDTO dto = service.verify("acme", "ACME.com", auth);

        assertNotNull(dto.verifiedAt());
        verify(tenantDomainRepository).save(pending);
    }

    @Test
    void verifyRejectsADomainAnotherOrganizationAlreadyVerified() {
        tenantExists();
        TenantDomain other = new TenantDomain();
        other.setTenantId(UUID.randomUUID());
        other.setVerifiedAt(Instant.now());
        when(tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), "acme.com"))
                .thenReturn(Optional.of(pending));
        when(tenantDomainRepository.findByDomainIgnoreCaseAndVerifiedAtIsNotNull("acme.com")).thenReturn(List.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verify("acme", "acme.com", auth));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addRejectsPublicEmailDomains() {
        tenantExists();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.add("acme", "Gmail.com", auth));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addRejectsMalformedDomains() {
        tenantExists();

        assertThrows(ResponseStatusException.class, () -> service.add("acme", "not a domain", auth));
        assertThrows(ResponseStatusException.class, () -> service.add("acme", "localhost", auth));
    }

    @Test
    void autoJoinIgnoresUnverifiedEmailAddresses() {
        User user = new User("Eve", "eve@acme.com", "hash");

        assertFalse(service.autoJoinVerifiedDomain(user));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void autoJoinAddsAVerifiedAddressAsMember() {
        User user = new User("Ada", "ada@acme.com", "hash");
        user.setId(UUID.randomUUID());
        user.setEmailVerifiedAt(Instant.now());
        pending.setVerifiedAt(Instant.now());
        when(tenantDomainRepository.findByDomainIgnoreCaseAndVerifiedAtIsNotNull("acme.com")).thenReturn(List.of(pending));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        assertTrue(service.autoJoinVerifiedDomain(user));
        verify(membershipRepository).save(any(TenantMembership.class));
    }
}
