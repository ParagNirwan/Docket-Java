package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentChunkRepository;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.service.FolderAccessService;
import com.google.cloud.vertexai.VertexAI;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQueryServiceTest {
    private static final String QUESTION = "What is the termination clause?";
    private static final String SUMMARY_QUESTION = "Summarize this document";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private VertexAI vertexAI;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderAccessService folderAccessService;

    @InjectMocks
    private AiQueryService aiQueryService;

    private UUID tenantId;
    private UUID documentId;
    private UUID folderId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        folderId = UUID.randomUUID();
    }

    private Document documentWithStatus(FileStatus status) {
        Document document = new Document();
        document.setId(documentId);
        document.setTenantId(tenantId);
        document.setFolderId(folderId);
        document.setFileStatus(status);
        return document;
    }

    @Test
    void query_softDeletedDocument_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.SOFT_DELETED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, documentChunkRepository);
    }

    @Test
    void query_documentNotFoundOrOtherTenant_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, documentChunkRepository);
    }

    @Test
    void query_documentInTrayUserCannotSee_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, documentChunkRepository);
    }

    @Test
    void query_workspaceSearchByMember_excludesRestrictedTrays() {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(QUESTION))).thenReturn(List.of(new float[]{0.1f}));
        when(documentChunkRepository.findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(5)))
                .thenReturn(List.of());

        // Nothing the member may see matched, so there are no sources and no call to the AI model.
        assertTrue(aiQueryService.query(authentication, tenantId, QUESTION, null).getSources().isEmpty());

        verify(documentChunkRepository).findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(5));
        verifyNoInteractions(vertexAI);
    }

    @Test
    void query_workspaceSearchByAdmin_includesRestrictedTrays() {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(true);
        when(embeddingService.embed(List.of(QUESTION))).thenReturn(List.of(new float[]{0.1f}));
        when(documentChunkRepository.findNearestByTenant(eq(tenantId), anyString(), eq(true), eq(5)))
                .thenReturn(List.of());

        aiQueryService.query(authentication, tenantId, QUESTION, null);

        verify(documentChunkRepository).findNearestByTenant(eq(tenantId), anyString(), eq(true), eq(5));
    }

    @Test
    void query_documentSearches_passVisibilityFilterToRepository() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(QUESTION))).thenReturn(List.of(new float[]{0.1f}));

        aiQueryService.query(authentication, tenantId, QUESTION, documentId);
        aiQueryService.query(authentication, tenantId, SUMMARY_QUESTION, documentId);

        verify(documentChunkRepository).findNearestByTenantAndDocument(
                eq(tenantId), eq(documentId), anyString(), eq(false), eq(5));
        verify(documentChunkRepository).findByDocumentOrdered(tenantId, documentId, false, 20);
    }
}
