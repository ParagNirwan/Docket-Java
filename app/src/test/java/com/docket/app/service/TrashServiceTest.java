package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrashServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private FolderAccessService folderAccessService;
    @Mock private RetentionGuard retentionGuard;
    @Mock private RetentionService retentionService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TrashService trashService;

    private UUID tenantId;
    private Folder trash;
    private Folder inbox;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        trash = tray("Trash");
        inbox = tray("Inbox");

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenantId);
        document.setFolderId(trash.getId());
        document.setFileStatus(FileStatus.SOFT_DELETED);
        document.setOriginalFileName("a.pdf");
        document.setStoredFileName("stored.pdf");
        document.setDeletedAt(Instant.now());
        when(documentRepository.findByIdAndTenantId(document.getId(), tenantId)).thenReturn(Optional.of(document));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(folderAccessService.canAccessFolder(authentication, tenantId, trash.getId())).thenReturn(true);
    }

    private Folder tray(String name) {
        Folder tray = new Folder();
        tray.setId(UUID.randomUUID());
        tray.setTenantId(tenantId);
        tray.setName(name);
        tray.setIsDefault(true);
        when(folderRepository.findByTenantIdAndIsTrayIsTrueAndIsDefaultIsTrueAndName(tenantId, name)).thenReturn(Optional.of(tray));
        when(folderRepository.findById(tray.getId())).thenReturn(Optional.of(tray));
        return tray;
    }

    @Test
    void list_handlesADocumentWhoseFolderIsGoneAndThatAJobDeleted() {
        Tenant tenant = tenantRepository.findBySlug(SLUG).orElseThrow();
        tenant.setTrashRetentionDays(30);
        when(documentRepository.findByTenantIdAndFileStatus(eq(tenantId), eq(FileStatus.SOFT_DELETED), any()))
                .thenReturn(new PageImpl<>(List.of(document)));

        var listed = trashService.list(SLUG, 0, 50, authentication).getContent().getFirst();

        assertNull(listed.originalFolderName());
        assertNull(listed.deletedByName());
    }

    @Test
    void moveToTrash_remembersWhereItCameFrom() {
        UUID folderId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        document.setFolderId(folderId);
        document.setFileStatus(FileStatus.ACTIVE);

        trashService.moveToTrash(document, actor);

        assertEquals(FileStatus.SOFT_DELETED, document.getFileStatus());
        assertEquals(folderId, document.getOriginalFolderId());
        assertEquals(trash.getId(), document.getFolderId());
        assertEquals(actor, document.getDeletedBy());
    }

    @Test
    void restore_goesBackToItsFolderAndUnderItsPolicies() {
        UUID folderId = UUID.randomUUID();
        document.setOriginalFolderId(folderId);

        trashService.restore(SLUG, document.getId(), authentication);

        assertEquals(FileStatus.ACTIVE, document.getFileStatus());
        assertEquals(folderId, document.getFolderId());
        assertNull(document.getDeletedAt());
        assertNull(document.getOriginalFolderId());
        verify(retentionService).applyOnEntry(document, folderId);
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_RESTORED), any(), any(), any());
    }

    @Test
    void restore_goesToTheInboxIfTheFolderIsGone() {
        trashService.restore(SLUG, document.getId(), authentication);

        assertEquals(inbox.getId(), document.getFolderId());
    }

    @Test
    void restore_takenNameGetsASuffix() {
        document.setOriginalFolderId(inbox.getId());
        when(documentRepository.existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCase(
                tenantId, inbox.getId(), FileStatus.ACTIVE, "a.pdf")).thenReturn(true);

        trashService.restore(SLUG, document.getId(), authentication);

        assertEquals("a (1).pdf", document.getOriginalFileName());
    }

    @Test
    void restore_documentNotInTrashLooksMissing() {
        document.setFileStatus(FileStatus.ACTIVE);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> trashService.restore(SLUG, document.getId(), authentication));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void restore_needsThePermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.DOCUMENTS_DELETE);

        assertThrows(ResponseStatusException.class, () -> trashService.restore(SLUG, document.getId(), authentication));

        assertEquals(FileStatus.SOFT_DELETED, document.getFileStatus());
    }

    @Test
    void purge_removesTheRowThenAsksForTheFileToGo() {
        trashService.purge(SLUG, document.getId(), RetentionGuard.Override.NONE, authentication);

        InOrder order = inOrder(documentRepository, eventPublisher);
        order.verify(documentRepository).delete(document);
        order.verify(documentRepository).flush();
        order.verify(eventPublisher).publishEvent(new StoredFileDeletedEvent("stored.pdf"));
    }

    @Test
    void purge_lockedDocumentKeepsItsRowAndFile() {
        when(retentionGuard.assertMutable(document, RetentionGuard.Action.PURGE, RetentionGuard.Override.NONE, authentication))
                .thenThrow(new DocumentLockedException("locked", null, null, true, false));

        assertThrows(DocumentLockedException.class,
                () -> trashService.purge(SLUG, document.getId(), RetentionGuard.Override.NONE, authentication));

        verify(documentRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void emptyTrash_leavesLockedDocumentsAndCountsThem() {
        Document held = new Document();
        held.setId(UUID.randomUUID());
        held.setLegalHold(true);
        Document retained = new Document();
        retained.setId(UUID.randomUUID());
        retained.setRetainUntil(Instant.now().plus(Duration.ofDays(1)));
        when(documentRepository.findByTenantIdAndFileStatus(eq(tenantId), eq(FileStatus.SOFT_DELETED), any()))
                .thenReturn(new PageImpl<>(List.of(document, held, retained)));

        TrashService.EmptyTrashResult result = trashService.emptyTrash(SLUG, authentication);

        assertEquals(1, result.purged());
        assertEquals(2, result.skipped());
        verify(documentRepository).delete(document);
        verify(documentRepository, never()).delete(held);
        verify(documentRepository, never()).delete(retained);
    }

    @Test
    void purgeExpired_isAuditedAsTheSystem() {
        assertTrue(trashService.purgeExpired(document.getId()));

        verify(auditService).recordSystem(eq(tenantId), eq(AuditAction.DOCUMENT_PURGED), eq("DOCUMENT"), eq(document.getId()), any());
    }

    @Test
    void purgeExpired_skipsADocumentHeldSinceItWasPicked() {
        document.setLegalHold(true);

        assertFalse(trashService.purgeExpired(document.getId()));

        verify(documentRepository, never()).delete(any());
    }

    @Test
    void requireNotInTrash_coversFoldersInsideItToo() {
        Folder inside = new Folder();
        inside.setId(UUID.randomUUID());
        inside.setRootTrayId(trash.getId());
        when(folderRepository.findById(inside.getId())).thenReturn(Optional.of(inside));

        assertThrows(ResponseStatusException.class, () -> trashService.requireNotInTrash(trash.getId()));
        assertThrows(ResponseStatusException.class, () -> trashService.requireNotInTrash(inside.getId()));
        assertDoesNotThrow(() -> trashService.requireNotInTrash(inbox.getId()));
    }
}
