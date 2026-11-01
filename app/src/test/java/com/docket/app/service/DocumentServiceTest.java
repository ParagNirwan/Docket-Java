package com.docket.app.service;

import com.docket.app.dto.CopyDocumentRequestDTO;
import com.docket.app.dto.UpdateDocumentRequestDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.ai.DocumentUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderAccessService folderAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private RetentionGuard retentionGuard;

    @Mock
    private RetentionService retentionService;

    @Mock
    private TrashService trashService;

    @InjectMocks
    private DocumentService documentService;

    private UUID tenantId;
    private UUID folderId;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        folderId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenantId);
        document.setFolderId(folderId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setStoredFileName("stored.pdf");
    }

    private void folderIsHidden() {
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);
    }

    private void documentExists() {
        when(documentRepository.findByIdAndTenantId(document.getId(), tenantId)).thenReturn(Optional.of(document));
    }

    private void assertNotFound(Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getDocuments_hiddenFolder_throws404() {
        folderIsHidden();

        assertNotFound(() -> documentService.getDocuments(SLUG, folderId, authentication, Pageable.unpaged()));
        verifyNoInteractions(documentRepository);
    }

    @Test
    void uploadDocument_hiddenFolder_throws404AndStoresNothing() {
        folderIsHidden();
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});

        assertNotFound(() -> documentService.uploadDocument(SLUG, folderId, file, authentication));
        verifyNoInteractions(storageService, documentRepository);
    }

    @Test
    void getDocumentById_hiddenFolder_throws404() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.getDocumentById(SLUG, document.getId(), authentication));
    }

    @Test
    void downloadDocument_hiddenFolder_throws404AndReadsNoFile() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.downloadDocument(SLUG, document.getId(), authentication));
        verifyNoInteractions(storageService);
    }

    @Test
    void softDeleteDocument_hiddenFolder_throws404AndDeletesNothing() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.softDeleteDocument(SLUG, document.getId(), authentication));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void downloadDocument_visibleFolder_returnsFile() {
        documentExists();
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(storageService.retrieve("stored.pdf")).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        assertSame(document, documentService.downloadDocument(SLUG, document.getId(), authentication).document());
    }

    private void renamableDocument() {
        document.setOriginalFileName("report.pdf");
        documentExists();
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    @Test
    void updateDocument_rename_writesOnlyTheChangedColumnsAndAudits() {
        renamableDocument();

        var result = documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO("summary.pdf", null), authentication);

        assertEquals("summary.pdf", result.getOriginalFileName());
        verify(documentRepository).updateNameAndFolder(eq(document.getId()), eq("summary.pdf"), eq(folderId), any());
        // A full save could overwrite an indexing status written in the meantime.
        verify(documentRepository, never()).save(any());
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_RENAMED), any(), any(), any());
    }

    @Test
    void updateDocument_changingTheExtension_isRejected() {
        renamableDocument();

        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY, () -> documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO("report.exe", null), authentication));
    }

    @Test
    void updateDocument_nameTaken_conflicts() {
        renamableDocument();
        when(documentRepository.existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCaseAndIdNot(
                tenantId, folderId, FileStatus.ACTIVE, "summary.pdf", document.getId())).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO("summary.pdf", null), authentication));
        verify(documentRepository, never()).updateNameAndFolder(any(), any(), any(), any());
    }

    @Test
    void updateDocument_moveIntoHiddenFolder_looksLikeItDoesNotExist() {
        renamableDocument();
        UUID hidden = UUID.randomUUID();
        when(folderAccessService.canAccessFolder(authentication, tenantId, hidden)).thenReturn(false);

        assertNotFound(() -> documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO(null, hidden), authentication));
        verify(documentRepository, never()).updateNameAndFolder(any(), any(), any(), any());
    }

    @Test
    void updateDocument_move_auditsSourceAndDestination() {
        renamableDocument();
        UUID target = UUID.randomUUID();
        when(folderAccessService.canAccessFolder(authentication, tenantId, target)).thenReturn(true);

        var result = documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO(null, target), authentication);

        assertEquals(target, result.getFolderId());
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_MOVED), any(), any(), any());
    }

    @Test
    void updateDocument_deletedDocument_isNotFound() {
        renamableDocument();
        document.setFileStatus(FileStatus.SOFT_DELETED);

        assertNotFound(() -> documentService.updateDocument(SLUG, document.getId(),
                new UpdateDocumentRequestDTO("summary.pdf", null), authentication));
    }

    @Test
    void copyDocument_storesItsOwnFileUnderAFreeNameAndIndexesIt() {
        renamableDocument();
        document.setContentType("application/pdf");
        User copier = new User("Ada Lovelace", "ada@example.com", "encoded");
        copier.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(copier));
        when(storageService.retrieve("stored.pdf")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(documentRepository.existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCase(
                tenantId, folderId, FileStatus.ACTIVE, "report.pdf")).thenReturn(true);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var copy = documentService.copyDocument(SLUG, document.getId(), new CopyDocumentRequestDTO(null), authentication);

        assertEquals("report (1).pdf", copy.getOriginalFileName());
        ArgumentCaptor<String> storedName = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(any(), storedName.capture(), eq("application/pdf"));
        assertNotEquals("stored.pdf", storedName.getValue());
        verify(eventPublisher).publishEvent(any(DocumentUploadedEvent.class));
        verify(auditService).record(eq(tenantId), eq(copier.getId()), eq(AuditAction.DOCUMENT_COPIED), any(), any(), any());
    }

    @Test
    void copyDocument_requiresCreatePermission() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(tenantAuthorizationService)
                .requirePermission(authentication, tenantId, Permission.DOCUMENTS_CREATE);

        assertStatus(HttpStatus.FORBIDDEN, () -> documentService.copyDocument(SLUG, document.getId(),
                new CopyDocumentRequestDTO(null), authentication));
        verifyNoInteractions(storageService);
    }

    @Test
    void softDeleteDocument_movesToTrashOnlyAfterTheGuardAgrees() {
        documentExists();
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(retentionGuard.assertMutable(document, RetentionGuard.Action.DELETE, RetentionGuard.Override.NONE, authentication))
                .thenThrow(new DocumentLockedException("locked", null, null, true, false));

        assertThrows(DocumentLockedException.class,
                () -> documentService.softDeleteDocument(SLUG, document.getId(), authentication));

        verify(trashService, never()).moveToTrash(any(), any());
    }

    @Test
    void softDeleteDocument_overriddenLockIsDroppedBeforeTheDocumentGoesToTrash() {
        documentExists();
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        RetentionGuard.Override override = new RetentionGuard.Override(true, "Filed in error");
        when(retentionGuard.assertMutable(document, RetentionGuard.Action.DELETE, override, authentication)).thenReturn(true);
        User user = new User("Ada", "ada@example.com", "x");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        documentService.softDeleteDocument(SLUG, document.getId(), override, authentication);

        verify(retentionService).clearLock(document);
        verify(trashService).moveToTrash(document, user.getId());
    }

    @Test
    void updateDocument_lockedDocumentCantMove() {
        documentExists();
        UUID destination = UUID.randomUUID();
        document.setOriginalFileName("a.pdf");
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderAccessService.canAccessFolder(authentication, tenantId, destination)).thenReturn(true);
        when(retentionGuard.assertMutable(document, RetentionGuard.Action.MOVE, RetentionGuard.Override.NONE, authentication))
                .thenThrow(new DocumentLockedException("locked", null, null, false, false));

        assertThrows(DocumentLockedException.class, () -> documentService.updateDocument(
                SLUG, document.getId(), new UpdateDocumentRequestDTO(null, destination), authentication));

        verify(documentRepository, never()).updateNameAndFolder(any(), any(), any(), any());
    }

    @Test
    void updateDocument_movedDocumentComesUnderTheNewFoldersPolicies() {
        documentExists();
        UUID destination = UUID.randomUUID();
        document.setOriginalFileName("a.pdf");
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderAccessService.canAccessFolder(authentication, tenantId, destination)).thenReturn(true);

        documentService.updateDocument(SLUG, document.getId(), new UpdateDocumentRequestDTO(null, destination), authentication);

        verify(retentionService).applyOnEntry(document, destination);
    }
}
