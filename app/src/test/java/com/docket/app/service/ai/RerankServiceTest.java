package com.docket.app.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankServiceTest {

    private static final String QUESTION = "When does the lease end?";

    @Mock
    private RankClient rankClient;

    @InjectMocks
    private RerankService rerankService;

    private final UUID documentId = UUID.randomUUID();

    private RetrievedChunk chunk(int chunkIndex) {
        return new RetrievedChunk(documentId, "lease.pdf", chunkIndex, "text " + chunkIndex, 1, 1, "Term", 0.3, false);
    }

    @Test
    void rerank_ordersByScoreAndKeepsTheScore() throws Exception {
        List<RetrievedChunk> candidates = List.of(chunk(1), chunk(2), chunk(3));
        when(rankClient.rank(eq(QUESTION), any(), eq(Duration.ofMillis(500))))
                .thenReturn(Map.of("0", 0.2, "1", 0.9, "2", 0.5));

        List<RetrievedChunk> result = rerankService.rerank(QUESTION, candidates);

        assertEquals(List.of(2, 3, 1), result.stream().map(RetrievedChunk::chunkIndex).toList());
        assertEquals(0.9, result.get(0).rerankScore());
    }

    @Test
    void rerank_sendsDocumentNameAndHeadingAsTheTitle() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RankClient.Record>> records = ArgumentCaptor.forClass(List.class);
        when(rankClient.rank(any(), records.capture(), any())).thenReturn(Map.of("0", 0.5, "1", 0.4));

        rerankService.rerank(QUESTION, List.of(chunk(1), chunk(2)));

        assertEquals(new RankClient.Record("0", "lease.pdf - Term", "text 1"), records.getValue().get(0));
    }

    @Test
    void rerank_onlyRanksTheBestCandidates() throws Exception {
        ReflectionTestUtils.setField(rerankService, "maxCandidates", 2);
        when(rankClient.rank(any(), any(), any())).thenReturn(Map.of("0", 0.5, "1", 0.7));

        List<RetrievedChunk> result = rerankService.rerank(QUESTION, List.of(chunk(1), chunk(2), chunk(3)));

        assertEquals(List.of(2, 1), result.stream().map(RetrievedChunk::chunkIndex).toList());
    }

    @Test
    void rerank_timeoutOrFailure_keepsTheOriginalOrderWithoutScores() throws Exception {
        List<RetrievedChunk> candidates = List.of(chunk(1), chunk(2));
        when(rankClient.rank(any(), any(), any())).thenThrow(new TimeoutException());

        List<RetrievedChunk> result = rerankService.rerank(QUESTION, candidates);

        assertSame(candidates, result);
        assertNull(result.get(0).rerankScore());
    }

    @Test
    void rerank_switchedOff_doesNotCallTheApi() {
        ReflectionTestUtils.setField(rerankService, "enabled", false);
        List<RetrievedChunk> candidates = List.of(chunk(1), chunk(2));

        assertSame(candidates, rerankService.rerank(QUESTION, candidates));
        verifyNoInteractions(rankClient);
    }
}
