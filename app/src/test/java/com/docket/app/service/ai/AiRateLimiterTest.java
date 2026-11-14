package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRateLimiterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Spy
    private DocketAiProperties aiProperties = new DocketAiProperties();

    @InjectMocks
    private AiRateLimiter aiRateLimiter;

    @BeforeEach
    void setUp() {
        aiProperties.getRateLimit().setMaxRequests(10);
        aiProperties.getRateLimit().setWindowMillis(60_000L);
    }

    @Test
    void twoInstancesShareSamePerUserCounter() {
        // Simulate first instance allowing the request
        AiRateLimiter.Hit hit1 = new AiRateLimiter.Hit(1, 59);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit1);

        // First call
        aiRateLimiter.checkAllowed("user-123");

        // Simulate second instance seeing the incremented count
        AiRateLimiter.Hit hit2 = new AiRateLimiter.Hit(2, 58);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit2);

        // Second call from "another instance" - same user
        aiRateLimiter.checkAllowed("user-123");

        // Verify both calls used the same hashed key
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), any(RowMapper.class), keyCaptor.capture(), eq(60L), eq(60L), eq(60L));
        assertEquals(TokenHasher.hash("ai:user-123"), keyCaptor.getAllValues().get(0));
        assertEquals(TokenHasher.hash("ai:user-123"), keyCaptor.getAllValues().get(1));
    }

    @Test
    void differentUsersDoNotShareCounters() {
        AiRateLimiter.Hit hit1 = new AiRateLimiter.Hit(1, 59);
        AiRateLimiter.Hit hit2 = new AiRateLimiter.Hit(1, 59);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit1, hit2);

        aiRateLimiter.checkAllowed("user-1");
        aiRateLimiter.checkAllowed("user-2");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), any(RowMapper.class), keyCaptor.capture(), eq(60L), eq(60L), eq(60L));

        assertEquals(TokenHasher.hash("ai:user-1"), keyCaptor.getAllValues().get(0));
        assertEquals(TokenHasher.hash("ai:user-2"), keyCaptor.getAllValues().get(1));
    }

    @Test
    void concurrentRequestsCannotExceedLimit() throws InterruptedException {
        int limit = 10;
        int threadCount = 20;
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        // Use thenAnswer to return sequential values based on call count
        AtomicInteger callCount = new AtomicInteger(0);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count <= limit) {
                        return new AiRateLimiter.Hit(count, 60 - count);
                    } else {
                        return new AiRateLimiter.Hit(limit + 1, 1);
                    }
                });

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    aiRateLimiter.checkAllowed("concurrent-user");
                    allowed.incrementAndGet();
                } catch (RateLimitExceededException e) {
                    rejected.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Exactly limit requests should be allowed, rest rejected
        assertEquals(limit, allowed.get());
        assertEquals(threadCount - limit, rejected.get());
    }

    @Test
    void requestAboveLimitThrows429WithRetryAfter() {
        AiRateLimiter.Hit hit = new AiRateLimiter.Hit(11, 30);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit);

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> aiRateLimiter.checkAllowed("user-123")
        );

        assertEquals(30, ex.retryAfterSeconds());
    }

    @Test
    void tryAcquireReturnsFalseWhenLimitExceeded() {
        AiRateLimiter.Hit hit = new AiRateLimiter.Hit(11, 30);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit);

        boolean allowed = aiRateLimiter.tryAcquire("user-123");
        assertEquals(false, allowed);
    }

    @Test
    void tryAcquireReturnsTrueWhenUnderLimit() {
        AiRateLimiter.Hit hit = new AiRateLimiter.Hit(5, 45);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(60L), eq(60L), eq(60L)))
                .thenReturn(hit);

        boolean allowed = aiRateLimiter.tryAcquire("user-123");
        assertEquals(true, allowed);
    }

    @Test
    void getCurrentCountReturnsZeroForUnknownUser() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(60L), anyString(), eq(60L)))
                .thenReturn(List.of());

        int count = aiRateLimiter.getCurrentCount("unknown-user");
        assertEquals(0, count);
    }

    @Test
    void getCurrentCountReturnsCurrentAttempts() {
        AiRateLimiter.Hit hit = new AiRateLimiter.Hit(7, 20);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(60L), anyString(), eq(60L)))
                .thenReturn(List.of(hit));

        int count = aiRateLimiter.getCurrentCount("user-123");
        assertEquals(7, count);
    }

    @Test
    void resetRemovesUserCounter() {
        aiRateLimiter.reset("user-123");

        verify(jdbcTemplate).update(
                eq("DELETE FROM auth_rate_limits WHERE bucket_key = ?"),
                eq(TokenHasher.hash("ai:user-123"))
        );
    }

    @Test
    void usesCorrectWindowFromConfiguration() {
        aiProperties.getRateLimit().setMaxRequests(5);
        aiProperties.getRateLimit().setWindowMillis(120_000L); // 2 minutes

        AiRateLimiter.Hit hit = new AiRateLimiter.Hit(1, 119);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), eq(120L), eq(120L), eq(120L)))
                .thenReturn(hit);

        aiRateLimiter.checkAllowed("user-123");

        verify(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), anyString(), eq(120L), eq(120L), eq(120L));
    }
}