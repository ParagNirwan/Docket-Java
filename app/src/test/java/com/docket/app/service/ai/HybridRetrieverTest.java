package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.repository.ChunkSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    private static final float[] EMBEDDING = {0.1f, 0.2f};

    @Mock
    private ChunkSearchRepository chunkSearchRepository;

    @Spy
    private DocketAiProperties aiProperties = new DocketAiProperties();

    @InjectMocks
    private HybridRetriever hybridRetriever;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private RetrievedChunk chunk(int chunkIndex) {
        return new RetrievedChunk(documentId, "lease.pdf", chunkIndex, "text", null, null, null, 0.3, false);
    }

    @Test
    void search_passesVisibilityFilterAndScopeToBothWorkspaceSearches() {
        AiSearchScope scope = new AiSearchScope(UUID.randomUUID(), null, null, null, null, null, null);

        hybridRetriever.search(tenantId, null, "question", EMBEDDING, false, scope);

        verify(chunkSearchRepository).findNearest(eq(tenantId), anyString(), eq(false), eq(scope), eq(30));
        verify(chunkSearchRepository).findByKeywords(
                eq(tenantId), eq("question"), anyString(), eq(false), eq(scope), eq(30));
    }

    @Test
    void search_singleDocument_replacesTheScopeWithThatDocument() {
        AiSearchScope ignored = new AiSearchScope(UUID.randomUUID(), null, null, null, null, null, null);
        AiSearchScope onlyTheDocument = AiSearchScope.ofDocument(documentId);

        hybridRetriever.search(tenantId, documentId, "question", EMBEDDING, true, ignored);

        verify(chunkSearchRepository).findNearest(eq(tenantId), anyString(), eq(true), eq(onlyTheDocument), eq(30));
        verify(chunkSearchRepository).findByKeywords(
                eq(tenantId), eq("question"), anyString(), eq(true), eq(onlyTheDocument), eq(30));
    }

    @Test
    void search_hybridSwitchedOff_onlyUsesVectorSearch() {
        ReflectionTestUtils.setField(hybridRetriever, "hybrid", false);
        when(chunkSearchRepository.findNearest(eq(tenantId), anyString(), eq(false), eq(AiSearchScope.NONE), eq(30)))
                .thenReturn(List.of(chunk(1)));

        assertEquals(1, hybridRetriever.search(
                tenantId, null, "question", EMBEDDING, false, AiSearchScope.NONE).size());

        verify(chunkSearchRepository).findNearest(
                eq(tenantId), anyString(), eq(false), eq(AiSearchScope.NONE), eq(30));
        verifyNoMoreInteractions(chunkSearchRepository);
    }

    @Test
    void summaries_sendsTheEmbeddingAsAVector() {
        hybridRetriever.summaries(tenantId, EMBEDDING, false, AiSearchScope.NONE, 0.45, 6);

        verify(chunkSearchRepository).findNearestSummaries(
                tenantId, "[0.1,0.2]", false, AiSearchScope.NONE, 0.45, 6);
    }

    @Test
    void fuse_chunkFoundByBothSearchesRanksFirst() {
        List<RetrievedChunk> result = HybridRetriever.fuse(
                List.of(chunk(1), chunk(2), chunk(3)),
                List.of(chunk(3), chunk(4)));

        assertEquals(3, result.get(0).chunkIndex());
        assertEquals(4, result.size());
    }

    @Test
    void fuse_tieGoesToTheExactKeywordMatch() {
        RetrievedChunk exact = new RetrievedChunk(documentId, "a", 9, "t", null, null, null, 0.9, true);

        List<RetrievedChunk> result = HybridRetriever.fuse(List.of(chunk(1)), List.of(exact));

        assertTrue(result.get(0).exactMatch());
        assertEquals(9, result.get(0).chunkIndex());
    }

    @Test
    void fuse_keepsExactMatchFlagWhenBothSearchesFindTheChunk() {
        RetrievedChunk exact = new RetrievedChunk(documentId, "a", 1, "t", null, null, null, 0.9, true);

        assertTrue(HybridRetriever.fuse(List.of(chunk(1)), List.of(exact)).get(0).exactMatch());
    }

    @Test
    void keywordQuery_keepsOnlyIdentifiersWhenThereAreAny() {
        assertEquals("INV-2024-0042", HybridRetriever.keywordQuery("What is the total on invoice INV-2024-0042?"));
        assertEquals("SKU123 A-77", HybridRetriever.keywordQuery("compare SKU123 and A-77"));
    }

    @Test
    void keywordQuery_ordinaryQuestionsAndYearsAreSearchedAsWritten() {
        assertEquals("What changed in 2024?", HybridRetriever.keywordQuery("What changed in 2024?"));
        assertEquals("Who is the landlord?", HybridRetriever.keywordQuery("Who is the landlord?"));
    }
}
