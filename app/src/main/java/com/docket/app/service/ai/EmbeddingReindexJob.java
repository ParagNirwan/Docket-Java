package com.docket.app.service.ai;

import com.docket.app.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Brings documents up to the current embedding version a few at a time. It runs on the
// scheduler's threads rather than the upload pool, so new uploads never wait behind it.
// The default pace matches RateLimiterService's budget of 10 AI requests a minute.
@Component
@RequiredArgsConstructor
@Profile("gcp")
public class EmbeddingReindexJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingReindexJob.class);
    private static final Duration LEASE = Duration.ofHours(1);

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService documentProcessingService;

    @Value("${docket.ai.reindex.enabled}")
    private boolean enabled = true;

    @Value("${docket.ai.reindex.batch-size}")
    private int batchSize = 10;

    @Value("${docket.ai.reindex.max-attempts}")
    private int maxAttempts = 3;

    @Value("${docket.ai.embedding.version}")
    private int embeddingVersion = 1;

    @Scheduled(fixedDelayString = "${docket.ai.reindex.interval-ms}",
            initialDelayString = "${docket.ai.reindex.interval-ms}")
    public void reindexNextBatch() {
        if (!enabled) {
            return;
        }

        List<UUID> batch = documentRepository.claimForReindex(
                embeddingVersion, maxAttempts, Instant.now().minus(LEASE), batchSize);

        if (!batch.isEmpty()) {
            log.info("Re-indexing {} documents to embedding version {}", batch.size(), embeddingVersion);
        }
        batch.forEach(documentProcessingService::processDocument);
    }
}
