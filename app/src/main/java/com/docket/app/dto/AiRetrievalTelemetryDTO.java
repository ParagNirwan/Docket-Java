package com.docket.app.dto;

import java.util.List;
import java.util.Map;

// Telemetry for AI retrieval quality, exposed to tenant admins for debugging search issues.
public record AiRetrievalTelemetryDTO(
        // Distance distribution of retrieved chunks (bucket -> count)
        Map<String, Long> distanceHistogram,
        // How many chunks passed the rerank threshold vs total candidates
        long candidatesRetrieved,
        long candidatesAfterThreshold,
        // Rerank score distribution
        Map<String, Long> rerankScoreHistogram,
        // Exact match count (keyword hits that bypass distance filter)
        long exactMatchCount,
        // Average latency in milliseconds
        double avgEmbeddingLatencyMs,
        double avgRetrievalLatencyMs,
        double avgGenerationLatencyMs,
        double avgTotalQueryLatencyMs,
        // Token usage estimates
        long totalInputTokensEstimated,
        long totalOutputTokensEstimated,
        // Top unmatched queries (queries that returned no results)
        List<UnmatchedQueryDTO> topUnmatchedQueries
) {
    public record UnmatchedQueryDTO(
            String query,
            int occurrenceCount,
            String firstSeen,
            String lastSeen
    ) {}
}