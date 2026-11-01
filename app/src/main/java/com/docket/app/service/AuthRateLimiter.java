package com.docket.app.service;

import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

// Counts attempts per key in fixed time windows, using Postgres so that every backend
// pod shares the same counts. Keys are hashed before they reach the table. Future
// endpoints such as share links (#71) can use this as is.
@Service
@RequiredArgsConstructor
public class AuthRateLimiter {
    // The single statement does the read and the write together, so simultaneous
    // requests can't both slip through on the same count.
    private static final String HIT_SQL = """
            INSERT INTO auth_rate_limits AS l (bucket_key, window_start, attempts)
            VALUES (?, now(), 1)
            ON CONFLICT (bucket_key) DO UPDATE SET
                attempts = CASE WHEN l.window_start + make_interval(secs => ?) <= now() THEN 1 ELSE l.attempts + 1 END,
                window_start = CASE WHEN l.window_start + make_interval(secs => ?) <= now() THEN now() ELSE l.window_start END
            RETURNING attempts, CEIL(EXTRACT(EPOCH FROM l.window_start + make_interval(secs => ?) - now()))::bigint
            """;
    private static final String PEEK_SQL = """
            SELECT attempts, CEIL(EXTRACT(EPOCH FROM window_start + make_interval(secs => ?) - now()))::bigint
            FROM auth_rate_limits
            WHERE bucket_key = ? AND window_start + make_interval(secs => ?) > now()
            """;

    private final JdbcTemplate jdbcTemplate;

    // Counts an attempt and throws a 429 if it goes over the limit.
    public void check(String key, int limit, Duration window) {
        Hit hit = hit(key, window);
        if (hit.attempts() > limit) {
            throw new RateLimitExceededException(hit.retryAfterSeconds());
        }
    }

    // Counts an attempt and reports whether it was within the limit, for callers that
    // must stay silent about being limited.
    public boolean tryAcquire(String key, int limit, Duration window) {
        return hit(key, window).attempts() <= limit;
    }

    // Counts an attempt and returns the new total.
    public int increment(String key, Duration window) {
        return hit(key, window).attempts();
    }

    // Throws a 429 if the key is already at the limit, without counting this attempt.
    public void requireBelow(String key, int limit, Duration window) {
        long seconds = window.toSeconds();
        List<Hit> hits = jdbcTemplate.query(
                PEEK_SQL,
                (rs, rowNum) -> new Hit(rs.getInt(1), rs.getLong(2)),
                seconds, TokenHasher.hash(key), seconds);
        if (!hits.isEmpty() && hits.getFirst().attempts() >= limit) {
            throw new RateLimitExceededException(hits.getFirst().retryAfterSeconds());
        }
    }

    public void reset(String key) {
        jdbcTemplate.update("DELETE FROM auth_rate_limits WHERE bucket_key = ?", TokenHasher.hash(key));
    }

    // The longest window in use is well under a day.
    void purgeOldCounters() {
        jdbcTemplate.update("DELETE FROM auth_rate_limits WHERE window_start < now() - INTERVAL '1 day'");
    }

    private Hit hit(String key, Duration window) {
        long seconds = window.toSeconds();
        return jdbcTemplate.queryForObject(
                HIT_SQL,
                (rs, rowNum) -> new Hit(rs.getInt(1), rs.getLong(2)),
                TokenHasher.hash(key), seconds, seconds, seconds);
    }

    private record Hit(int attempts, long retryAfterSeconds) {
    }
}
