package com.docket.app.service.ai;

import com.docket.app.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
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
    private DocumentChunkRepository documentChunkRepository;

    @InjectMocks
    private HybridRetriever hybridRetriever;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private Object[] row(int chunkIndex, Double distance, boolean exactMatch) {
        return new Object[]{documentId, "lease.pdf", chunkIndex, "text " + chunkIndex, 4, 5, "Terms", distance, exactMatch};
    }

    private RetrievedChunk chunk(int chunkIndex) {
        return new RetrievedChunk(documentId, "lease.pdf", chunkIndex, "text", null, null, null, 0.3, false);
    }

    private List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    @Test
    void search_passesVisibilityFilterToBothWorkspaceSearches() {
        when(documentChunkRepository.findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(30)))
                .thenReturn(List.of());
        when(documentChunkRepository.findByKeywordsInTenant(eq(tenantId), eq("question"), anyString(), eq(false), eq(30)))
                .thenReturn(List.of());

        hybridRetriever.search(tenantId, null, "question", EMBEDDING, false);

        verify(documentChunkRepository).findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(30));
        verify(documentChunkRepository).findByKeywordsInTenant(eq(tenantId), eq("question"), anyString(), eq(false), eq(30));
    }

    @Test
    void search_passesDocumentAndVisibilityFilterToBothDocumentSearches() {
        when(documentChunkRepository.findNearestByTenantAndDocument(
                eq(tenantId), eq(documentId), anyString(), eq(true), eq(30))).thenReturn(List.of());
        when(documentChunkRepository.findByKeywordsInDocument(
                eq(tenantId), eq(documentId), eq("question"), anyString(), eq(true), eq(30))).thenReturn(List.of());

        hybridRetriever.search(tenantId, documentId, "question", EMBEDDING, true);

        verify(documentChunkRepository).findNearestByTenantAndDocument(
                eq(tenantId), eq(documentId), anyString(), eq(true), eq(30));
        verify(documentChunkRepository).findByKeywordsInDocument(
                eq(tenantId), eq(documentId), eq("question"), anyString(), eq(true), eq(30));
    }

    @Test
    void search_hybridSwitchedOff_onlyUsesVectorSearch() {
        ReflectionTestUtils.setField(hybridRetriever, "hybrid", false);
        when(documentChunkRepository.findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(30)))
                .thenReturn(rows(row(1, 0.2, false)));

        assertEquals(1, hybridRetriever.search(tenantId, null, "question", EMBEDDING, false).size());

        verify(documentChunkRepository).findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(30));
        verifyNoMoreInteractions(documentChunkRepository);
    }

    @Test
    void search_mapsColumnsByPosition() {
        when(documentChunkRepository.findNearestByTenant(eq(tenantId), anyString(), eq(false), eq(30)))
                .thenReturn(rows(row(7, 0.25, false)));
        when(documentChunkRepository.findByKeywordsInTenant(eq(tenantId), anyString(), anyString(), eq(false), eq(30)))
                .thenReturn(List.of());

        RetrievedChunk result = hybridRetriever.search(tenantId, null, "question", EMBEDDING, false).get(0);

        assertEquals(new RetrievedChunk(documentId, "lease.pdf", 7, "text 7", 4, 5, "Terms", 0.25, false), result);
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
