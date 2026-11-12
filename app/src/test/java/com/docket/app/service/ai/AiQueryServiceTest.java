package com.docket.app.service.ai;

import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.entity.Document;
import com.docket.app.enums.AiQueryMode;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQueryServiceTest {
    private static final String QUESTION = "What is the termination clause?";
    private static final String SUMMARY_QUESTION = "Give me the gist of this";
    private static final float[] EMBEDDING = {0.1f};

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private HybridRetriever hybridRetriever;

    @Mock
    private RerankService rerankService;

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
        // Re-ranking switched off: candidates come back as they went in
        lenient().when(rerankService.rerank(any(), any())).thenAnswer(call -> call.getArgument(1));
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
        when(hybridRetriever.search(tenantId, null, QUESTION, EMBEDDING, false, AiSearchScope.NONE)).thenReturn(List.of(chunks));
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

        verify(hybridRetriever).search(tenantId, null, QUESTION, EMBEDDING, false, AiSearchScope.NONE);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void query_workspaceSearchByAdmin_includesRestrictedTrays() {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(true);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, null, QUESTION, EMBEDDING, true, AiSearchScope.NONE)).thenReturn(List.of());

        aiQueryService.query(authentication, tenantId, QUESTION, null);

        verify(hybridRetriever).search(tenantId, null, QUESTION, EMBEDDING, true, AiSearchScope.NONE);
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
        aiQueryService.query(authentication, tenantId, SUMMARY_QUESTION, documentId, AiQueryMode.SUMMARIZE);

        verify(hybridRetriever).search(tenantId, documentId, QUESTION, EMBEDDING, false, AiSearchScope.NONE);
        verify(hybridRetriever).firstChunks(tenantId, documentId, false, 200);
    }

    @Test
    void query_summaryModeUsesMapReduceForLongDocuments() throws IOException {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        List<RetrievedChunk> chunks = java.util.stream.IntStream.range(0, 15)
                .mapToObj(index -> new RetrievedChunk(documentId, "lease.pdf", index, "section " + index,
                        index + 1, index + 1, "Terms", null, false))
                .toList();
        when(hybridRetriever.firstChunks(tenantId, documentId, false, 200)).thenReturn(chunks);
        modelReplies("{\"answer\": \"Summary [1].\", \"citations\": [{\"sourceId\": 1}]}");

        AiQueryResponseDTO response = aiQueryService.query(
                authentication, tenantId, "Give me the gist", documentId, AiQueryMode.SUMMARIZE);

        assertEquals("Summary [1].", response.getAnswer());
        verify(geminiClient, times(3)).generate(any(), any(), any(GenerationConfig.class));
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
        searchReturns(chunk(1, 0.1, false), chunk(3, 0.2, false), chunk(5, 0.3, false));
        modelReplies("{\"answer\": \"Pay in 30 days [3].\", \"citations\": [{\"sourceId\": 3}, {\"sourceId\": 8}]}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertEquals("Pay in 30 days [1].", response.getAnswer());
        assertEquals(1, response.getSources().size());
        AiSourceChunkDTO source = response.getSources().get(0);
        assertEquals(5, source.getChunkIndex());
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
        when(hybridRetriever.search(tenantId, null, rewritten, EMBEDDING, false, AiSearchScope.NONE))
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

    @Test
    void query_scope_isPassedToTheSearchAndTheSummaries() {
        UUID trayId = UUID.randomUUID();
        AiSearchScope scope = new AiSearchScope(trayId, null, null, List.of("application/pdf"),
                Instant.parse("2026-01-01T00:00:00Z"), null, null);
        when(folderAccessService.canAccessFolder(authentication, tenantId, trayId)).thenReturn(true);
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, null, QUESTION, EMBEDDING, false, scope))
                .thenReturn(List.of(chunk(1, 0.9, false)));

        aiQueryService.query(authentication, tenantId, QUESTION, null, QUESTION, List.of(), AiQueryMode.ASK, scope);

        verify(hybridRetriever).search(tenantId, null, QUESTION, EMBEDDING, false, scope);
        verify(hybridRetriever).summaries(eq(tenantId), eq(EMBEDDING), eq(false), eq(scope), eq(0.45), eq(6));
    }

    @Test
    void query_scopedToTrayUserCannotSee_throws404BeforeSearching() {
        UUID trayId = UUID.randomUUID();
        AiSearchScope scope = new AiSearchScope(trayId, null, null, null, null, null, null);
        when(folderAccessService.canAccessFolder(authentication, tenantId, trayId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> aiQueryService.query(
                authentication, tenantId, QUESTION, null, QUESTION, List.of(), AiQueryMode.ASK, scope));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, hybridRetriever);
    }

    @Test
    void query_reranked_scoreDecidesInsteadOfDistance() throws IOException {
        // Chunk 1 is close in meaning but the re-ranker says it doesn't answer the question.
        // Chunk 2 is distant but does.
        RetrievedChunk near = chunk(1, 0.1, false);
        RetrievedChunk far = chunk(2, 0.9, false);
        searchReturns(near, far);
        when(rerankService.rerank(QUESTION, List.of(near, far)))
                .thenReturn(List.of(far.withRerankScore(0.92), near.withRerankScore(0.05)));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompt.capture(), any(GenerationConfig.class)))
                .thenReturn("{\"answer\": \"Yes [1].\", \"citations\": [{\"sourceId\": 1}]}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertTrue(prompt.getValue().contains("text 2"));
        assertFalse(prompt.getValue().contains("text 1"));
        assertEquals(0.92, response.getSources().get(0).getRelevanceScore(), 0.0001);
    }

    @Test
    void query_workspaceSearch_addsSummariesOfDocumentsNoChunkCovers() throws IOException {
        UUID otherDocument = UUID.randomUUID();
        searchReturns(chunk(1, 0.1, false));
        when(hybridRetriever.summaries(tenantId, EMBEDDING, false, AiSearchScope.NONE, 0.45, 6)).thenReturn(List.of(
                new RetrievedChunk(documentId, "lease.pdf", RetrievedChunk.SUMMARY_INDEX, "lease summary", null, null,
                        RetrievedChunk.SUMMARY_HEADING, 0.2, false),
                new RetrievedChunk(otherDocument, "policy.pdf", RetrievedChunk.SUMMARY_INDEX, "policy summary", null,
                        null, RetrievedChunk.SUMMARY_HEADING, 0.3, false)));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompt.capture(), any(GenerationConfig.class)))
                .thenReturn("{\"answer\": \"Both [1][2].\", \"citations\": []}");

        AiQueryResponseDTO response = aiQueryService.query(authentication, tenantId, QUESTION, null);

        assertTrue(prompt.getValue().contains("policy summary"));
        assertFalse(prompt.getValue().contains("lease summary"));
        assertEquals(otherDocument, response.getSources().get(1).getDocumentId());
    }

    @Test
    void query_singleDocument_doesNotSearchSummaries() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId))
                .thenReturn(Optional.of(documentWithStatus(FileStatus.ACTIVE)));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(embeddingService.embed(List.of(QUESTION), EmbeddingTaskType.RETRIEVAL_QUERY))
                .thenReturn(List.of(EMBEDDING));
        when(hybridRetriever.search(tenantId, documentId, QUESTION, EMBEDDING, false, AiSearchScope.NONE))
                .thenReturn(List.of(chunk(1, 0.9, false)));

        aiQueryService.query(authentication, tenantId, QUESTION, documentId);

        verify(hybridRetriever, never()).summaries(any(), any(), any(Boolean.class), any(), any(Double.class),
                any(Integer.class));
    }

    private static final class RecordingListener implements AiStreamListener {
        private final List<AiSourceChunkDTO> sources = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int sourceEvents;

        @Override
        public void onSources(List<AiSourceChunkDTO> retrieved) {
            sourceEvents++;
            sources.addAll(retrieved);
        }

        @Override
        public void onDelta(String delta) {
            text.append(delta);
        }
    }

    @SuppressWarnings("unchecked")
    private void modelStreams(String... pieces) throws IOException {
        doAnswer(call -> {
            java.util.function.Consumer<String> onText = call.getArgument(3);
            for (String piece : pieces) {
                onText.accept(piece);
            }
            return null;
        }).when(geminiClient).generateStream(any(), any(), any(GenerationConfig.class), any());
    }

    @Test
    void stream_sendsSourcesThenTextThatMatchesTheStoredAnswer() throws IOException {
        searchReturns(chunk(1, 0.1, false), chunk(3, 0.2, false), chunk(5, 0.3, false));
        // The marker is split across two pieces, and [3] must become [1]
        modelStreams("Pay in 30 days [", "3]. Late fees apply [3][1].");
        RecordingListener listener = new RecordingListener();

        AiQueryResponseDTO response = aiQueryService.stream(authentication, tenantId, QUESTION, null, QUESTION,
                List.of(), AiQueryMode.ASK, AiSearchScope.NONE, listener);

        assertEquals("Pay in 30 days [1]. Late fees apply [1][2].", response.getAnswer());
        assertEquals(response.getAnswer(), listener.text.toString());
        assertEquals(1, listener.sourceEvents);
        assertEquals(3, listener.sources.size());
        assertEquals(List.of(5, 1), response.getSources().stream().map(AiSourceChunkDTO::getChunkIndex).toList());
        verify(geminiClient, never()).generate(any(), any(), any(GenerationConfig.class));
    }

    @Test
    void stream_nothingRelevant_saysSoWithoutCallingTheModel() {
        searchReturns(chunk(1, 0.8, false));
        RecordingListener listener = new RecordingListener();

        AiQueryResponseDTO response = aiQueryService.stream(authentication, tenantId, QUESTION, null, QUESTION,
                List.of(), AiQueryMode.ASK, AiSearchScope.NONE, listener);

        assertEquals(AiQueryService.NOTHING_RELEVANT, response.getAnswer());
        assertEquals(AiQueryService.NOTHING_RELEVANT, listener.text.toString());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void stream_clientDisconnects_stopsInsteadOfReportingAnAiFailure() throws IOException {
        searchReturns(chunk(1, 0.1, false));
        modelStreams("Pay in ", "30 days.");
        AiStreamListener gone = new AiStreamListener() {
            @Override
            public void onSources(List<AiSourceChunkDTO> sources) {
            }

            @Override
            public void onDelta(String text) {
                throw new AiStreamCancelledException(new IOException("broken pipe"));
            }
        };

        assertThrows(AiStreamCancelledException.class, () -> aiQueryService.stream(authentication, tenantId,
                QUESTION, null, QUESTION, List.of(), AiQueryMode.ASK, AiSearchScope.NONE, gone));
    }

    @Test
    void addSummaries_keepsAtMostThree() {
        List<RetrievedChunk> summaries = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> new RetrievedChunk(UUID.randomUUID(), "doc" + i, RetrievedChunk.SUMMARY_INDEX, "s", null,
                        null, RetrievedChunk.SUMMARY_HEADING, 0.2, false))
                .toList();

        assertEquals(3, AiQueryService.addSummaries(List.of(), summaries).size());
    }

    @Test
    void diversifyAndMerge_capsDocumentsAndMergesAdjacentChunks() {
        UUID otherDocument = UUID.randomUUID();
        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk(documentId, "lease.pdf", 0, "alpha beta gamma delta", 1, 1, null, 0.1, false),
                new RetrievedChunk(documentId, "lease.pdf", 1, "gamma delta epsilon zeta", 1, 2, null, 0.2, false),
                new RetrievedChunk(otherDocument, "policy.pdf", 0, "different relevant content", 2, 2, null, 0.15,
                        false),
                new RetrievedChunk(documentId, "lease.pdf", 4, "another distinct chunk", 4, 4, null, 0.3, false),
                new RetrievedChunk(documentId, "lease.pdf", 5, "last distinct chunk", 5, 5, null, 0.35, false),
                new RetrievedChunk(documentId, "lease.pdf", 6, "third distinct chunk", 6, 6, null, 0.4, false),
                new RetrievedChunk(documentId, "lease.pdf", 7, "fourth distinct chunk", 7, 7, null, 0.45, false));

        List<RetrievedChunk> result = AiQueryService.diversifyAndMerge(candidates);

        assertEquals(4, result.size());
        assertTrue(result.get(0).chunkText().contains("epsilon zeta"));
        assertEquals(2, result.get(0).pageEnd());
        assertEquals(otherDocument, result.get(1).documentId());
        assertEquals(3, result.stream().filter(c -> c.documentId().equals(documentId)).count());
    }
}
