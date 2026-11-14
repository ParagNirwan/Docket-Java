package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Database-backed rate limiter for AI queries.
 * Uses PostgreSQL so that every backend pod shares the same counts.
 * Keys are hashed before they reach the table.
 */
@Service
@RequiredArgsConstructor
public class AiRateLimiter {

    // Atomic upsert: inserts a new counter or increments the existing one within the window.
    // Returns the new attempt count and seconds until the window resets.
    private static final String HIT_SQL = """
            INSERT INTO auth_rate_limits AS l (bucket_key, window_start, attempts)
            VALUES (?, now(), 1)
            ON CONFLICT (bucket_key) DO UPDATE SET
                attempts = CASE WHEN l.window_start + make_interval(secs => ?) <= now() THEN 1 ELSE l.attempts + 1 END,
                window_start = CASE WHEN l.window_start + make_interval(secs => ?) <= now() THEN now() ELSE l.window_start END
            RETURNING attempts, CEIL(EXTRACT(EPOCH FROM l.window_start + make_interval(secs => ?) - now()))::bigint
            """;

    // Read-only check: returns current count without incrementing.
    private static final String PEEK_SQL = """
            SELECT attempts, CEIL(EXTRACT(EPOCH FROM window_start + make_interval(secs => ?) - now()))::bigint
            FROM auth_rate_limits
            WHERE bucket_key = ? AND window_start + make_interval(secs => ?) > now()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DocketAiProperties aiProperties;

    /**
     * Checks if the user is allowed to make an AI query.
     * Throws RateLimitExceededException with retry-after if limit is exceeded.
     */
    public void checkAllowed(String userId) {
        Hit hit = hit(userId);
        int limit = aiProperties.getRateLimit().getMaxRequests();
        if (hit.attempts() > limit) {
            throw new RateLimitExceededException(hit.retryAfterSeconds());
        }
    }

    /**
     * Checks if the user is allowed without throwing (for callers that must stay silent).
     */
    public boolean tryAcquire(String userId) {
        return hit(userId).attempts() <= aiProperties.getRateLimit().getMaxRequests();
    }

    /**
     * Returns the current attempt count for a user without incrementing.
     */
    public int getCurrentCount(String userId) {
        long seconds = aiProperties.getRateLimit().getWindowMillis() / 1000;
        String key = "ai:" + userId;
        List<Hit> hits = jdbcTemplate.query(
                PEEK_SQL,
                (rs, rowNum) -> new Hit(rs.getInt(1), rs.getLong(2)),
                seconds, TokenHasher.hash(key), seconds);
        return hits.isEmpty() ? 0 : hits.getFirst().attempts();
    }

    /**
     * Resets the counter for a user (useful for testing).
     */
    public void reset(String userId) {
        String key = "ai:" + userId;
        jdbcTemplate.update("DELETE FROM auth_rate_limits WHERE bucket_key = ?", TokenHasher.hash(key));
    }

    private Hit hit(String userId) {
        long seconds = aiProperties.getRateLimit().getWindowMillis() / 1000;
        String key = "ai:" + userId;
        return jdbcTemplate.queryForObject(
                HIT_SQL,
                (rs, rowNum) -> new Hit(rs.getInt(1), rs.getLong(2)),
                TokenHasher.hash(key), seconds, seconds, seconds);
    }

    record Hit(int attempts, long retryAfterSeconds) {
    }
}