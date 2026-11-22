package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.service.DatabaseRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Database-backed rate limiter for AI queries.
 * Uses PostgreSQL so that every backend pod shares the same counts.
 * Keys are hashed before they reach the table.
 * Enforces both per-user and per-tenant limits to prevent any single tenant
 * from overwhelming the shared AI capacity across replicas.
 */
@Service
@RequiredArgsConstructor
public class AiRateLimiter {

    private final DatabaseRateLimiter databaseRateLimiter;
    private final DocketAiProperties aiProperties;

    /**
     * Checks if the user and their tenant are allowed to make an AI query.
     * Throws RateLimitExceededException with retry-after if either limit is exceeded.
     * Per-user limit is checked first (stricter), then per-tenant limit.
     */
    public void checkAllowed(String userId, UUID tenantId) {
        // Per-user limit
        String userKey = "ai:user:" + userId;
        int userLimit = aiProperties.getRateLimit().getMaxRequests();
        Duration userWindow = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        databaseRateLimiter.check(userKey, userLimit, userWindow);

        // Per-tenant limit (prevents a single tenant from consuming all capacity)
        String tenantKey = "ai:tenant:" + tenantId;
        int tenantLimit = aiProperties.getRateLimit().getTenantMaxRequests();
        Duration tenantWindow = Duration.ofMillis(aiProperties.getRateLimit().getTenantWindowMillis());
        databaseRateLimiter.check(tenantKey, tenantLimit, tenantWindow);
    }

    /**
     * Checks if the user and their tenant are allowed without throwing
     * (for callers that must stay silent about being limited).
     */
    public boolean tryAcquire(String userId, UUID tenantId) {
        String userKey = "ai:user:" + userId;
        int userLimit = aiProperties.getRateLimit().getMaxRequests();
        Duration userWindow = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        boolean userAllowed = databaseRateLimiter.tryAcquire(userKey, userLimit, userWindow);

        if (!userAllowed) {
            return false;
        }

        String tenantKey = "ai:tenant:" + tenantId;
        int tenantLimit = aiProperties.getRateLimit().getTenantMaxRequests();
        Duration tenantWindow = Duration.ofMillis(aiProperties.getRateLimit().getTenantWindowMillis());
        return databaseRateLimiter.tryAcquire(tenantKey, tenantLimit, tenantWindow);
    }

    /**
     * Returns the current attempt count for a user without incrementing.
     */
    public int getCurrentUserCount(String userId) {
        String key = "ai:user:" + userId;
        Duration window = Duration.ofMillis(aiProperties.getRateLimit().getWindowMillis());
        return databaseRateLimiter.peek(key, window);
    }

    /**
     * Returns the current attempt count for a tenant without incrementing.
     */
    public int getCurrentTenantCount(UUID tenantId) {
        String key = "ai:tenant:" + tenantId;
        Duration window = Duration.ofMillis(aiProperties.getRateLimit().getTenantWindowMillis());
        return databaseRateLimiter.peek(key, window);
    }

    /**
     * Resets the counter for a user (useful for testing).
     */
    public void resetUser(String userId) {
        String key = "ai:user:" + userId;
        databaseRateLimiter.reset(key);
    }

    /**
     * Resets the counter for a tenant (useful for testing).
     */
    public void resetTenant(UUID tenantId) {
        String key = "ai:tenant:" + tenantId;
        databaseRateLimiter.reset(key);
    }
}