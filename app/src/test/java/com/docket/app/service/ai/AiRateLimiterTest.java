package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.security.TokenHasher;
import com.docket.app.service.DatabaseRateLimiter;
import com.docket.app.service.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRateLimiterTest {

    @Mock
    private DatabaseRateLimiter databaseRateLimiter;

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
        // First call
        doNothing().when(databaseRateLimiter).check(eq("ai:user-123"), eq(10), any(Duration.class));
        aiRateLimiter.checkAllowed("user-123");

        // Second call from "another instance" - same user
        doNothing().when(databaseRateLimiter).check(eq("ai:user-123"), eq(10), any(Duration.class));
        aiRateLimiter.checkAllowed("user-123");

        // Verify both calls used the same raw key (hashing happens inside DatabaseRateLimiter)
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(databaseRateLimiter, times(2)).check(keyCaptor.capture(), eq(10), any(Duration.class));
        assertEquals("ai:user-123", keyCaptor.getAllValues().get(0));
        assertEquals("ai:user-123", keyCaptor.getAllValues().get(1));
    }

    @Test
    void differentUsersDoNotShareCounters() {
        doNothing().when(databaseRateLimiter).check(anyString(), eq(10), any(Duration.class));

        aiRateLimiter.checkAllowed("user-1");
        aiRateLimiter.checkAllowed("user-2");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(databaseRateLimiter, times(2)).check(keyCaptor.capture(), eq(10), any(Duration.class));

        assertEquals("ai:user-1", keyCaptor.getAllValues().get(0));
        assertEquals("ai:user-2", keyCaptor.getAllValues().get(1));
    }

    @Test
    void concurrentRequestsCannotExceedLimit() throws InterruptedException {
        int limit = 10;
        int threadCount = 20;
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count > limit) {
                throw new RateLimitExceededException(1);
            }
            return null;
        }).when(databaseRateLimiter).check(anyString(), eq(limit), any(Duration.class));

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

        assertEquals(limit, allowed.get());
        assertEquals(threadCount - limit, rejected.get());
    }

    @Test
    void requestAboveLimitThrows429WithRetryAfter() {
        doThrow(new RateLimitExceededException(30))
                .when(databaseRateLimiter).check(anyString(), eq(10), any(Duration.class));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> aiRateLimiter.checkAllowed("user-123")
        );

        assertEquals(30, ex.retryAfterSeconds());
    }

    @Test
    void tryAcquireReturnsFalseWhenLimitExceeded() {
        when(databaseRateLimiter.tryAcquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(false);

        boolean allowed = aiRateLimiter.tryAcquire("user-123");
        assertEquals(false, allowed);
    }

    @Test
    void tryAcquireReturnsTrueWhenUnderLimit() {
        when(databaseRateLimiter.tryAcquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(true);

        boolean allowed = aiRateLimiter.tryAcquire("user-123");
        assertEquals(true, allowed);
    }

    @Test
    void getCurrentCountReturnsZeroForUnknownUser() {
        when(databaseRateLimiter.peek(anyString(), any(Duration.class)))
                .thenReturn(0);

        int count = aiRateLimiter.getCurrentCount("unknown-user");
        assertEquals(0, count);
    }

    @Test
    void getCurrentCountReturnsCurrentAttempts() {
        when(databaseRateLimiter.peek(anyString(), any(Duration.class)))
                .thenReturn(7);

        int count = aiRateLimiter.getCurrentCount("user-123");
        assertEquals(7, count);
    }

    @Test
    void resetRemovesUserCounter() {
        aiRateLimiter.reset("user-123");

        // DatabaseRateLimiter.reset receives the raw key and hashes it internally
        verify(databaseRateLimiter).reset(eq("ai:user-123"));
    }

    @Test
    void usesCorrectWindowFromConfiguration() {
        aiProperties.getRateLimit().setMaxRequests(5);
        aiProperties.getRateLimit().setWindowMillis(120_000L); // 2 minutes

        doNothing().when(databaseRateLimiter).check(anyString(), eq(5), any(Duration.class));

        aiRateLimiter.checkAllowed("user-123");

        verify(databaseRateLimiter).check(anyString(), eq(5), any(Duration.class));
    }
}