package com.docket.app.service;

import com.docket.app.dto.ArchivePreviewDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantAuthorizationService tenantAuthorizationService;
    @Mock private FolderAccessService folderAccessService;
    @Mock private RetentionGuard retentionGuard;
    @Mock private RetentionService retentionService;
    @Mock private DocumentService documentService;
    @Mock private AuditService auditService;

    @InjectMocks
    private ArchiveService archiveService;

    private UUID tenantId;
    private UUID originalFolderId;
    private Folder archive;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        originalFolderId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        archive = new Folder();
        archive.setId(UUID.randomUUID());
        archive.setName("Archive");
        archive.setIsArchive(true);
        when(folderRepository.findFirstByTenantIdAndIsArchiveIsTrueAndIsTrayIsTrue(tenantId)).thenReturn(Optional.of(archive));
        when(folderRepository.findById(archive.getId())).thenReturn(Optional.of(archive));

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenantId);
        document.setFolderId(originalFolderId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setOriginalFileName("a.pdf");
        when(documentRepository.findByIdAndTenantId(document.getId(), tenantId)).thenReturn(Optional.of(document));
        when(folderAccessService.canAccessFolder(any(), eq(tenantId), any())).thenReturn(true);
    }

    @Test
    void archive_movesToTheArchiveTrayAndStartsTheClock() {
        archiveService.archive(SLUG, document.getId(), null, authentication);

        assertEquals(archive.getId(), document.getFolderId());
        assertEquals(originalFolderId, document.getArchivedFromFolderId());
        assertNotNull(document.getArchivedAt());
        verify(retentionService).applyOnEntry(document, archive.getId());
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_ARCHIVED), any(), any(), any());
    }

    @Test
    void archive_alreadyArchivedIsAConflict() {
        document.setArchivedAt(Instant.now());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> archiveService.archive(SLUG, document.getId(), null, authentication));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void archive_lockedDocumentCantMove() {
        when(retentionGuard.assertMutable(document, RetentionGuard.Action.MOVE, RetentionGuard.Override.NONE, authentication))
                .thenThrow(new DocumentLockedException("locked", null, null, true, false));

        assertThrows(DocumentLockedException.class, () -> archiveService.archive(SLUG, document.getId(), null, authentication));

        assertEquals(originalFolderId, document.getFolderId());
    }

    @Test
    void archive_chosenFolderHasToBeInAnArchiveTray() {
        Folder other = new Folder();
        other.setId(UUID.randomUUID());
        other.setIsArchive(false);
        when(folderRepository.findById(other.getId())).thenReturn(Optional.of(other));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> archiveService.archive(SLUG, document.getId(), other.getId(), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void archive_chosenFolderCanBeInsideTheArchiveTray() {
        Folder inside = new Folder();
        inside.setId(UUID.randomUUID());
        inside.setRootTrayId(archive.getId());
        when(folderRepository.findById(inside.getId())).thenReturn(Optional.of(inside));

        archiveService.archive(SLUG, document.getId(), inside.getId(), authentication);

        assertEquals(inside.getId(), document.getFolderId());
    }

    @Test
    void archiveAll_hasALimit() {
        List<UUID> tooMany = Collections.nCopies(101, UUID.randomUUID());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> archiveService.archiveAll(SLUG, tooMany, null, authentication));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void preview_changesNothing() {
        ArchivePreviewDTO preview = archiveService.preview(SLUG, document.getId(), null, authentication);

        assertEquals(archive.getId(), preview.folderId());
        assertEquals(originalFolderId, document.getFolderId());
        assertNull(document.getArchivedAt());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void unarchive_goesBackWhereItCameFromAndGetsItsNewFoldersPolicies() {
        document.setArchivedAt(Instant.now());
        document.setArchivedFromFolderId(originalFolderId);
        document.setFolderId(archive.getId());

        archiveService.unarchive(SLUG, document.getId(), authentication);

        assertEquals(originalFolderId, document.getFolderId());
        assertNull(document.getArchivedAt());
        verify(retentionService).clearLock(document);
        verify(retentionService).applyOnEntry(document, originalFolderId);
    }

    @Test
    void unarchive_refusedWhileLockedOrHeld() {
        document.setArchivedAt(Instant.now());
        document.setRetainUntil(Instant.now().plus(Duration.ofDays(1)));
        assertThrows(DocumentLockedException.class, () -> archiveService.unarchive(SLUG, document.getId(), authentication));

        document.setRetainUntil(null);
        document.setLegalHold(true);
        assertThrows(DocumentLockedException.class, () -> archiveService.unarchive(SLUG, document.getId(), authentication));

        verify(documentRepository, never()).save(any());
    }
}
