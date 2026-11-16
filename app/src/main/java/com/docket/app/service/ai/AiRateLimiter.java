package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.service.DatabaseRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Database-backed rate limiter for AI queries.
 * Uses PostgreSQL so that every backend pod shares the same counts.
 * Keys are hashed before they reach the table.
 */
@Service
@RequiredArgsConstructor
public class AiRateLimiter {

    private final DatabaseRateLimiter databaseRateLimiter;
    private final DocketAiProperties aiProperties;

    /**
     * Checks if the user is allowed to make an AI query.
     * Throws RateLimitExceededException with retry-after if limit is exceeded.
     */
    public void checkAllowed(String userId) {
        String key = "ai:" + userId;
        int limit = aiProperties.getRateLimit().getMaxRequests();
        Duration window = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        databaseRateLimiter.check(key, limit, window);
    }

    /**
     * Checks if the user is allowed without throwing (for callers that must stay silent).
     */
    public boolean tryAcquire(String userId) {
        String key = "ai:" + userId;
        int limit = aiProperties.getRateLimit().getMaxRequests();
        Duration window = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        return databaseRateLimiter.tryAcquire(key, limit, window);
    }

    /**
     * Returns the current attempt count for a user without incrementing.
     */
    public int getCurrentCount(String userId) {
        String key = "ai:" + userId;
        Duration window = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        return databaseRateLimiter.peek(key, window);
    }

    /**
     * Resets the counter for a user (useful for testing).
     */
    public void reset(String userId) {
        String key = "ai:" + userId;
        databaseRateLimiter.reset(key);
    }
}