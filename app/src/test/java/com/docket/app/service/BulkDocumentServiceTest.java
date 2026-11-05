package com.docket.app.service;

import com.docket.app.dto.BulkDocumentRequestDTO;
import com.docket.app.dto.BulkMoveRequestDTO;
import com.docket.app.dto.BulkOperationResultDTO;
import com.docket.app.dto.UpdateDocumentRequestDTO;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TagRepository;
import com.docket.app.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkDocumentServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private DocumentService documentService;
    @Mock
    private ArchiveService archiveService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private FolderAccessService folderAccessService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BulkDocumentService bulkDocumentService;

    @Test
    void bulkDelete_processesAllAndReportsSummary() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        doNothing().when(documentService).softDeleteDocument(SLUG, doc1, authentication);
        doThrow(new RuntimeException("Locked")).when(documentService).softDeleteDocument(SLUG, doc2, authentication);

        BulkOperationResultDTO result = bulkDocumentService.bulkDelete(SLUG, new BulkDocumentRequestDTO(List.of(doc1, doc2)), authentication);

        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals(1, result.errors().size());
    }

    @Test
    void bulkMove_movesEachDocument() {
        UUID doc1 = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();

        BulkOperationResultDTO result = bulkDocumentService.bulkMove(SLUG,
                new BulkMoveRequestDTO(List.of(doc1), targetFolderId), authentication);

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        verify(documentService).updateDocument(eq(SLUG), eq(doc1), any(UpdateDocumentRequestDTO.class), eq(authentication));
    }

    @Test
    void bulkArchive_archivesEachDocument() {
        UUID doc1 = UUID.randomUUID();

        BulkOperationResultDTO result = bulkDocumentService.bulkArchive(SLUG,
                new BulkDocumentRequestDTO(List.of(doc1)), authentication);

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        verify(archiveService).archive(eq(SLUG), eq(doc1), isNull(), eq(authentication));
    }
}

