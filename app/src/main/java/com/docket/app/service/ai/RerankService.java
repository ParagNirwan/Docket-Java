package com.docket.app.service.ai;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Retrieval compares a question and a chunk as two separate embeddings. The re-ranker reads
// them together, which is slower but much better at telling a real answer from a chunk that
// only shares the topic. So retrieval casts a wide net and this puts the best on top.
@Service
@RequiredArgsConstructor
@Profile("gcp")
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RankClient rankClient;

    @Value("${docket.ai.rerank.enabled}")
    private boolean enabled = true;

    @Value("${docket.ai.rerank.timeout-ms}")
    private long timeoutMs = 500;

    @Value("${docket.ai.rerank.max-candidates}")
    private int maxCandidates = 30;

    // Returns the best candidates with a rerankScore, highest first. If re-ranking is off,
    // fails or takes too long, the candidates come back unchanged and without scores, because
    // a less precise answer beats no answer.
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates) {
        if (!enabled || candidates.size() < 2) {
            return candidates;
        }

        List<RetrievedChunk> head = candidates.subList(0, Math.min(maxCandidates, candidates.size()));
        List<RankClient.Record> records = new ArrayList<>();
        for (int i = 0; i < head.size(); i++) {
            RetrievedChunk chunk = head.get(i);
            String title = chunk.heading() == null
                    ? chunk.documentName()
                    : chunk.documentName() + " - " + chunk.heading();
            records.add(new RankClient.Record(String.valueOf(i), title, chunk.chunkText()));
        }

        Timer.Sample timer = Timer.start(Metrics.globalRegistry);
        try {
            Map<String, Double> scores = rankClient.rank(question, records, Duration.ofMillis(timeoutMs));
            // The API charges per record ranked, so this is also the cost
            Metrics.counter("docket.ai.rerank.records").increment(records.size());

            List<RetrievedChunk> scored = new ArrayList<>();
            for (int i = 0; i < head.size(); i++) {
                Double score = scores.get(String.valueOf(i));
                if (score != null) {
                    Metrics.summary("docket.ai.rerank.score").record(score);
                    scored.add(head.get(i).withRerankScore(score));
                }
            }
            if (scored.isEmpty()) {
                throw new IllegalStateException("The ranker returned no scores");
            }
            scored.sort(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed());
            return List.copyOf(scored);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Metrics.counter("docket.ai.rerank.fallback").increment();
            log.warn("Re-ranking failed, keeping the retrieval order: {}", e.toString());
            return candidates;
        } finally {
            timer.stop(Metrics.timer("docket.ai.rerank.latency"));
        }
    }
}
