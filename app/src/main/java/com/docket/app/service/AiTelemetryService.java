package com.docket.app.service;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.dto.AiRateLimitTelemetryDTO;
import com.docket.app.dto.AiRetrievalTelemetryDTO;
import com.docket.app.entity.User;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.ai.AiRateLimiter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.DistributionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes AI retrieval and rate-limit telemetry for tenant admins.
 * Aggregates data from Micrometer metrics and database queries.
 */
@Service
@RequiredArgsConstructor
public class AiTelemetryService {

    private final JdbcTemplate jdbcTemplate;
    private final AiRateLimiter aiRateLimiter;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final DocketAiProperties aiProperties;

    public AiRetrievalTelemetryDTO getRetrievalTelemetry(UUID tenantId) {
        // Distance histogram from Micrometer
        Map<String, Long> distanceHistogram = buildDistanceHistogram();

        // Candidate counts
        long candidatesRetrieved = getMetricCount("docket.ai.retrieval.candidates");
        long candidatesAfterThreshold = getMetricCount("docket.ai.retrieval.after_threshold");

        // Rerank score histogram
        Map<String, Long> rerankScoreHistogram = buildRerankHistogram();

        // Exact match count
        long exactMatchCount = getMetricCount("docket.ai.retrieval.exact_matches");

        // Latency metrics
        double avgEmbeddingLatencyMs = getAvgLatency("docket.ai.embedding.latency");
        double avgRetrievalLatencyMs = getAvgLatency("docket.ai.retrieval.latency");
        double avgGenerationLatencyMs = getAvgLatency("docket.ai.generation.latency");
        double avgTotalQueryLatencyMs = getAvgLatency("docket.ai.query.latency");

        // Token estimates
        long totalInputTokens = getMetricSum("docket.ai.generation.input.tokens.estimated");
        long totalOutputTokens = getMetricSum("docket.ai.generation.output.tokens.estimated");

        // Top unmatched queries from database
        List<AiRetrievalTelemetryDTO.UnmatchedQueryDTO> topUnmatchedQueries = getTopUnmatchedQueries(tenantId);

        return new AiRetrievalTelemetryDTO(
                distanceHistogram,
                candidatesRetrieved,
                candidatesAfterThreshold,
                rerankScoreHistogram,
                exactMatchCount,
                avgEmbeddingLatencyMs,
                avgRetrievalLatencyMs,
                avgGenerationLatencyMs,
                avgTotalQueryLatencyMs,
                totalInputTokens,
                totalOutputTokens,
                topUnmatchedQueries
        );
    }

    public AiRateLimitTelemetryDTO getRateLimitTelemetry(UUID tenantId) {
        int userLimit = aiProperties.getRateLimit().getMaxRequests();
        long userWindow = aiProperties.getRateLimit().getWindowMillis();
        int tenantLimit = aiProperties.getRateLimit().getTenantMaxRequests();
        long tenantWindow = aiProperties.getRateLimit().getTenantWindowMillis();

        int currentTenantUsage = aiRateLimiter.getCurrentTenantCount(tenantId);

        // Get top users by querying the rate limit table
        List<AiRateLimitTelemetryDTO.TopUserUsageDTO> topUsers = getTopUsers(tenantId);

        // Rejections in last hour (from audit log)
        long rejectionsLastHour = getRejectionsLastHour(tenantId);

        return new AiRateLimitTelemetryDTO(
                userLimit,
                userWindow,
                tenantLimit,
                tenantWindow,
                currentTenantUsage,
                topUsers,
                rejectionsLastHour
        );
    }

    private Map<String, Long> buildDistanceHistogram() {
        DistributionSummary summary = Metrics.globalRegistry.get("docket.ai.retrieval.distance").summary();
        if (summary == null) {
            return Collections.emptyMap();
        }
        // DistributionSummary doesn't expose histogram buckets directly in Micrometer 1.x
        // Return aggregate stats instead
        Map<String, Long> histogram = new LinkedHashMap<>();
        histogram.put("count", (long) summary.count());
        histogram.put("total", (long) summary.totalAmount());
        histogram.put("max", (long) summary.max());
        return histogram;
    }

    private Map<String, Long> buildRerankHistogram() {
        DistributionSummary summary = Metrics.globalRegistry.get("docket.ai.rerank.score").summary();
        if (summary == null) {
            return Collections.emptyMap();
        }
        Map<String, Long> histogram = new LinkedHashMap<>();
        histogram.put("count", (long) summary.count());
        histogram.put("total", (long) summary.totalAmount());
        histogram.put("max", (long) summary.max());
        return histogram;
    }

    private long getMetricCount(String name) {
        return (long) Metrics.globalRegistry.get(name).summary().count();
    }

    private double getAvgLatency(String name) {
        return Metrics.globalRegistry.get(name).timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private long getMetricSum(String name) {
        return (long) Metrics.globalRegistry.get(name).summary().totalAmount();
    }

    private List<AiRetrievalTelemetryDTO.UnmatchedQueryDTO> getTopUnmatchedQueries(UUID tenantId) {
        // Query audit events for AI queries that returned no results
        String sql = """
                SELECT
                    ae.details->>'question' AS question,
                    COUNT(*) AS count,
                    MIN(ae.created_at) AS first_seen,
                    MAX(ae.created_at) AS last_seen
                FROM audit_events ae
                WHERE ae.tenant_id = ?
                  AND ae.action = 'AI_QUERY'
                  AND ae.created_at >= ?
                  AND (ae.details->>'source_count')::int = 0
                GROUP BY ae.details->>'question'
                ORDER BY count DESC
                LIMIT 10
                """;

        Instant since = Instant.now().minus(Duration.ofDays(7));
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new AiRetrievalTelemetryDTO.UnmatchedQueryDTO(
                        rs.getString("question"),
                        rs.getInt("count"),
                        rs.getTimestamp("first_seen").toInstant().toString(),
                        rs.getTimestamp("last_seen").toInstant().toString()
                ),
                tenantId, Timestamp.from(since)).stream().collect(Collectors.toList());
    }

    private List<AiRateLimitTelemetryDTO.TopUserUsageDTO> getTopUsers(UUID tenantId) {
        // Get active members in this tenant and their current rate limit counts
        List<com.docket.app.entity.TenantMembership> memberships =
                membershipRepository.findByTenantIdAndMemberStatus(tenantId, com.docket.app.enums.MemberStatus.ACTIVE);
        List<AiRateLimitTelemetryDTO.TopUserUsageDTO> result = new ArrayList<>();

        int userLimit = aiProperties.getRateLimit().getMaxRequests();

        for (com.docket.app.entity.TenantMembership membership : memberships) {
            User user = userRepository.findById(membership.getUserId()).orElse(null);
            if (user == null) continue;
            int count = aiRateLimiter.getCurrentUserCount(user.getId().toString());
            double utilization = userLimit > 0 ? (count * 100.0 / userLimit) : 0;
            result.add(new AiRateLimitTelemetryDTO.TopUserUsageDTO(
                    user.getId().toString(),
                    user.getEmail(),
                    count,
                    userLimit,
                    utilization
            ));
        }

        // Sort by utilization descending and take top 10
        return result.stream()
                .sorted(Comparator.comparingDouble(AiRateLimitTelemetryDTO.TopUserUsageDTO::utilizationPercent).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private long getRejectionsLastHour(UUID tenantId) {
        String sql = """
                SELECT COUNT(*)
                FROM audit_events
                WHERE tenant_id = ?
                  AND action = 'AI_QUERY_RATE_LIMITED'
                  AND created_at >= ?
                """;
        Instant since = Instant.now().minus(Duration.ofHours(1));
        Long count = jdbcTemplate.queryForObject(sql, Long.class, tenantId, Timestamp.from(since));
        return count != null ? count : 0L;
    }
}