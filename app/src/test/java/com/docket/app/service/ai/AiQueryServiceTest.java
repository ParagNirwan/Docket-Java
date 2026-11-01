package com.docket.app.service.ai;

import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.entity.Document;
import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.service.FolderAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.GenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQueryServiceTest {
    private static final String QUESTION = "What is the termination clause?";
    private static final String SUMMARY_QUESTION = "Summarize this document";
    private static final float[] EMBEDDING = {0.1f};

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private HybridRetriever hybridRetriever;

    @Mock
    private GeminiClient geminiClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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

    private RetrievedChunk chunk(int chunkIndex, double distance, boolean exactMatch) {
        return new RetrievedChunk(documentId, "lease.pdf", chunkIndex, "text " + chunkIndex, 4, 5, "Payment Terms",
                distance, exactMatch);
    }

    private void searchReturns(RetrievedChunk... chunks) {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, null, QUESTION, EMBEDDING, false)).thenReturn(List.of(chunks));
    }

    private void modelReplies(String json) throws IOException {
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenReturn(json);
    }

    @Test
    void query_softDeletedDocument_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.SOFT_DELETED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, hybridRetriever);
    }

    @Test
    void query_documentNotFoundOrOtherTenant_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, hybridRetriever);
    }

    @Test
    void query_documentInTrayUserCannotSee_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, hybridRetriever);
    }

    @Test
    void query_workspaceSearchByMember_excludesRestrictedTrays() {
        searchReturns();

        // Nothing the member may see matched, so there are no sources and no call to the AI model.
        assertTrue(aiQueryService.query(authentication, tenantId, QUESTION, null).getSources().isEmpty());

        verify(hybridRetriever).search(tenantId, null, QUESTION, EMBEDDING, false);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void query_workspaceSearchByAdmin_includesRestrictedTrays() {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(true);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, null, QUESTION, EMBEDDING, true)).thenReturn(List.of());

        aiQueryService.query(authentication, tenantId, QUESTION, null);

        verify(hybridRetriever).search(tenantId, null, QUESTION, EMBEDDING, true);
    }

    @Test
    void query_documentSearches_passVisibilityFilterToRetriever() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));

        aiQueryService.query(authentication, tenantId, QUESTION, documentId);
        aiQueryService.query(authentication, tenantId, SUMMARY_QUESTION, documentId);

        verify(hybridRetriever).search(tenantId, documentId, QUESTION, EMBEDDING, false);
        verify(hybridRetriever).firstChunks(tenantId, documentId, false, 20);
    }

    @Test
    void query_nothingCloseEnough_skipsTheModelAndSaysSo() {
        searchReturns(chunk(1, 0.8, false), chunk(2, 0.95, false));

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertEquals(AiQueryService.NOTHING_RELEVANT, response.getAnswer());
        assertTrue(response.getSources().isEmpty());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void query_fallsBackToBestChunkWithinLooserBound() throws IOException {
        searchReturns(chunk(1, 0.58, false), chunk(2, 0.5, false), chunk(3, 0.7, false));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompt.capture(), any(GenerationConfig.class)))
                .thenReturn("{\"answer\": \"Not enough information.\", \"citations\": []}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertEquals("Not enough information.", response.getAnswer());
        assertTrue(response.getSources().isEmpty());
        assertTrue(prompt.getValue().contains("text 2"));
        assertFalse(prompt.getValue().contains("text 1"));
    }

    @Test
    void query_exactKeywordMatch_survivesTheThreshold() throws IOException {
        searchReturns(chunk(1, 0.9, true), chunk(2, 0.9, false));
        modelReplies("{\"answer\": \"Total is 50 [1].\", \"citations\": [{\"sourceId\": 1}]}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertEquals(1, response.getSources().size());
        assertEquals(1, response.getSources().get(0).getChunkIndex());
    }

    @Test
    void query_returnsOnlyCitedSourcesWithMatchingNumbers() throws IOException {
        searchReturns(chunk(1, 0.1, false), chunk(2, 0.2, false), chunk(3, 0.3, false));
        modelReplies("{\"answer\": \"Pay in 30 days [3].\", \"citations\": [{\"sourceId\": 3}, {\"sourceId\": 8}]}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertEquals("Pay in 30 days [1].", response.getAnswer());
        assertEquals(1, response.getSources().size());
        AiSourceChunkDTO source = response.getSources().get(0);
        assertEquals(3, source.getChunkIndex());
        assertEquals(0.7, source.getRelevanceScore(), 0.0001);
        assertEquals(4, source.getPageStart());
        assertEquals(5, source.getPageEnd());
        assertEquals("Payment Terms", source.getHeading());
    }

    @Test
    void query_followUp_searchesWithRewriteAndAnswersOriginalWithHistory() throws IOException {
        String rewritten = "When does the lease expire?";
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(rewritten), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, null, rewritten, EMBEDDING, false))
                .thenReturn(List.of(chunk(1, 0.1, false)));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompt.capture(), any(GenerationConfig.class)))
                .thenReturn("{\"answer\": \"In May [1].\", \"citations\": []}");

        aiQueryService.query(authentication, tenantId, "When does it expire?", null, rewritten,
                List.of(new ConversationTurn("user", "Who is the landlord?")));

        assertTrue(prompt.getValue().contains("--- QUESTION ---\nWhen does it expire?"));
        assertTrue(prompt.getValue().contains("user: Who is the landlord?"));
    }

    @Test
    void query_modelFails_returns503() throws IOException {
        searchReturns(chunk(1, 0.1, false));
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenThrow(new IOException("down"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void query_unfinishedJsonFromModel_returns503() throws IOException {
        searchReturns(chunk(1, 0.1, false));
        modelReplies("{\"answer\": \"Pay in");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(authentication, tenantId, QUESTION, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }
}
