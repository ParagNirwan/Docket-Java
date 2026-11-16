package com.docket.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Auth-specific rate limiter using the shared database-backed implementation.
 * Keys are prefixed with their purpose (login:ip:, login:account:, etc.).
 */
@Service
@RequiredArgsConstructor
public class AuthRateLimiter {

    private final DatabaseRateLimiter databaseRateLimiter;

    // Counts an attempt and throws a 429 if it goes over the limit.
    public void check(String key, int limit, Duration window) {
        databaseRateLimiter.check(key, limit, window);
    }

    // Counts an attempt and reports whether it was within the limit, for callers that
    // must stay silent about being limited.
    public boolean tryAcquire(String key, int limit, Duration window) {
        return databaseRateLimiter.tryAcquire(key, limit, window);
    }

    // Counts an attempt and returns the new total.
    public int increment(String key, Duration window) {
        return databaseRateLimiter.increment(key, window);
    }

    // Throws a 429 if the key is already at the limit, without counting this attempt.
    public void requireBelow(String key, int limit, Duration window) {
        databaseRateLimiter.requireBelow(key, limit, window);
    }

    public void reset(String key) {
        databaseRateLimiter.reset(key);
    }

    void purgeOldCounters() {
        databaseRateLimiter.purgeOldCounters();
    }
}