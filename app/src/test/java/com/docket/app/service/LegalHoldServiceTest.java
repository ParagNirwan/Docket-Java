package com.docket.app.service;

import com.docket.app.dto.LegalHoldRequestDTO;
import com.docket.app.dto.LegalHoldTargetsDTO;
import com.docket.app.dto.SearchResultDTO;
import com.docket.app.entity.LegalHold;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentSearchRepository;
import com.docket.app.repository.LegalHoldRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalHoldServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock private LegalHoldRepository legalHoldRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private DocumentSearchRepository documentSearchRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private DocumentService documentService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LegalHoldService service;

    private UUID tenantId;
    private LegalHold hold;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(SLUG);
        tenant.setName("Acme");
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        User user = new User("Ada", "ada@example.com", "x");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        hold = new LegalHold();
        hold.setId(UUID.randomUUID());
        hold.setTenantId(tenantId);
        hold.setName("Case 42");
        when(legalHoldRepository.findByIdAndTenantId(hold.getId(), tenantId)).thenReturn(Optional.of(hold));
        when(legalHoldRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            LegalHold saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private LegalHoldRequestDTO request(LegalHoldTargetsDTO targets) {
        return new LegalHoldRequestDTO(" Case 42 ", " Litigation ", targets);
    }

    @Test
    void create_coversDocumentsAndFoldersAndTellsPeople() {
        UUID documentId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(legalHoldRepository.addDocuments(any(), eq(tenantId), eq(List.of(documentId)), any())).thenReturn(1);
        when(legalHoldRepository.addFolder(any(), eq(tenantId), eq(folderId), any())).thenReturn(3);

        service.create(SLUG, request(new LegalHoldTargetsDTO(List.of(documentId), List.of(folderId), null)), authentication);

        verify(legalHoldRepository).markDocumentsHeld(any());
        verify(auditService).record(eq(tenantId), any(), eq(AuditAction.LEGAL_HOLD_APPLIED), eq("LEGAL_HOLD"), any(), any());
        verify(notificationService).notifyPermissionHolders(eq(tenantId), eq(Permission.RETENTION_READ),
                eq(NotificationType.LEGAL_HOLD_APPLIED), any(), any(), any());
    }

    @Test
    void create_coversASearchResultSet() {
        UUID match = UUID.randomUUID();
        when(documentSearchRepository.search(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(
                List.of(new SearchResultDTO(match, "a.pdf", null, 1L, null, Instant.now(), null, null, null, null, null, null)),
                PageRequest.of(0, 200), 1));
        when(legalHoldRepository.addDocuments(any(), eq(tenantId), eq(List.of(match)), any())).thenReturn(1);

        service.create(SLUG, request(new LegalHoldTargetsDTO(null, null, "contract")), authentication);

        verify(legalHoldRepository).markDocumentsHeld(any());
    }

    @Test
    void create_thatCoversNothingIsRefused() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(SLUG, request(new LegalHoldTargetsDTO(List.of(UUID.randomUUID()), null, null)), authentication));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verify(legalHoldRepository, never()).markDocumentsHeld(any());
    }

    @Test
    void create_needsThePermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.RETENTION_MANAGE);

        assertThrows(ResponseStatusException.class,
                () -> service.create(SLUG, request(new LegalHoldTargetsDTO(List.of(UUID.randomUUID()), null, null)), authentication));

        verify(legalHoldRepository, never()).saveAndFlush(any());
    }

    @Test
    void release_isOwnerOnly() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.RETENTION_OVERRIDE);

        assertThrows(ResponseStatusException.class, () -> service.release(SLUG, hold.getId(), authentication));

        verify(legalHoldRepository, never()).refreshDocumentsHeld(any());
    }

    @Test
    void release_marksTheHoldBeforeWorkingOutWhatsStillHeld() {
        service.release(SLUG, hold.getId(), authentication);

        assertNotNull(hold.getReleasedAt());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(legalHoldRepository);
        order.verify(legalHoldRepository).saveAndFlush(hold);
        order.verify(legalHoldRepository).refreshDocumentsHeld(hold.getId());
        verify(auditService).record(eq(tenantId), any(), eq(AuditAction.LEGAL_HOLD_RELEASED), eq("LEGAL_HOLD"), eq(hold.getId()), any());
    }

    @Test
    void release_twiceIsAConflict() {
        hold.setReleasedAt(Instant.now());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.release(SLUG, hold.getId(), authentication));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void addDocuments_toAReleasedHoldIsAConflict() {
        hold.setReleasedAt(Instant.now());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.addDocuments(SLUG, hold.getId(), new LegalHoldTargetsDTO(List.of(UUID.randomUUID()), null, null), authentication));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void aHoldInAnotherOrganizationLooksMissing() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.release(SLUG, UUID.randomUUID(), authentication));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}
