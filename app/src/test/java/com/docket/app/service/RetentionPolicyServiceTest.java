package com.docket.app.service;

import com.docket.app.dto.RetentionPolicyRequestDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.RetentionPolicy;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.OnExpiry;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.RetentionPolicyRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionPolicyServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock private RetentionPolicyRepository policyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private RetentionService retentionService;
    @Mock private AuditService auditService;

    @InjectMocks
    private RetentionPolicyService service;

    private UUID tenantId;
    private Folder folder;
    private RetentionPolicy compliance;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setTenantId(tenantId);
        folder.setName("Archive");
        folder.setIsDefault(true);
        when(folderRepository.findById(folder.getId())).thenReturn(Optional.of(folder));

        User user = new User("Ada", "ada@example.com", "x");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            RetentionPolicy saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        compliance = new RetentionPolicy();
        compliance.setId(UUID.randomUUID());
        compliance.setTenantId(tenantId);
        compliance.setFolderId(folder.getId());
        compliance.setRetentionDays(2555);
        compliance.setLockMode(LockMode.COMPLIANCE);
        compliance.setClockStart(ClockStart.ARCHIVED_AT);
        compliance.setAppliesToSubfolders(true);
        compliance.setOnExpiry(OnExpiry.NOTIFY);
        when(policyRepository.findByIdAndTenantId(compliance.getId(), tenantId)).thenReturn(Optional.of(compliance));
    }

    private RetentionPolicyRequestDTO request(int days, LockMode mode, ClockStart clock, boolean subfolders) {
        return new RetentionPolicyRequestDTO(folder.getId(), days, mode, clock, subfolders, OnExpiry.NOTIFY);
    }

    private void assertConflict(RetentionPolicyRequestDTO request) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.update(SLUG, compliance.getId(), request, authentication));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(retentionService, never()).applyPolicy(any());
    }

    @Test
    void create_appliesToExistingDocuments() {
        service.create(SLUG, request(2555, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, true), authentication);

        verify(retentionService).applyPolicy(any());
    }

    @Test
    void create_needsThePermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.RETENTION_MANAGE);

        assertThrows(ResponseStatusException.class,
                () -> service.create(SLUG, request(10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true), authentication));

        verify(policyRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_folderInAnotherOrganizationLooksMissing() {
        folder.setTenantId(UUID.randomUUID());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(SLUG, request(10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true), authentication));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void create_refusedOnTheTrash() {
        folder.setName("Trash");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(SLUG, request(10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void compliance_canBeExtended() {
        service.update(SLUG, compliance.getId(), request(3000, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, true), authentication);

        assertEquals(3000, compliance.getRetentionDays());
        verify(retentionService).applyPolicy(compliance);
    }

    @Test
    void compliance_cantBeShortened() {
        assertConflict(request(10, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, true));
    }

    @Test
    void compliance_cantBecomeGovernance() {
        assertConflict(request(2555, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true));
    }

    @Test
    void compliance_cantChangeItsClock() {
        assertConflict(request(2555, LockMode.COMPLIANCE, ClockStart.UPLOADED_AT, true));
    }

    @Test
    void compliance_cantStopCoveringSubfolders() {
        assertConflict(request(2555, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, false));
    }

    @Test
    void compliance_cantBeDeleted() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.delete(SLUG, compliance.getId(), authentication));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(policyRepository, never()).delete(any());
    }

    @Test
    void governance_canBeShortenedAndDeleted() {
        compliance.setLockMode(LockMode.GOVERNANCE);

        service.update(SLUG, compliance.getId(), request(10, LockMode.GOVERNANCE, ClockStart.UPLOADED_AT, false), authentication);
        service.delete(SLUG, compliance.getId(), authentication);

        assertEquals(10, compliance.getRetentionDays());
        verify(policyRepository).delete(compliance);
    }

    @Test
    void aPolicyCantMoveToAnotherFolder() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.update(SLUG, compliance.getId(),
                new RetentionPolicyRequestDTO(UUID.randomUUID(), 3000, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, true, OnExpiry.NOTIFY),
                authentication));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }
}
