package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiProperties.getRateLimit().setMaxRequests(10);
        aiProperties.getRateLimit().setWindowMillis(60_000L);
        aiProperties.getRateLimit().setTenantMaxRequests(100);
        aiProperties.getRateLimit().setTenantWindowMillis(60_000L);
    }

    @Test
    void twoInstancesShareSamePerUserCounter() {
        // First call
        doNothing().when(databaseRateLimiter).check(eq("ai:user:user-123"), eq(10), any(Duration.class));
        doNothing().when(databaseRateLimiter).check(eq("ai:tenant:" + tenantId), eq(100), any(Duration.class));
        aiRateLimiter.checkAllowed("user-123", tenantId);

        // Second call from "another instance" - same user
        doNothing().when(databaseRateLimiter).check(eq("ai:user:user-123"), eq(10), any(Duration.class));
        doNothing().when(databaseRateLimiter).check(eq("ai:tenant:" + tenantId), eq(100), any(Duration.class));
        aiRateLimiter.checkAllowed("user-123", tenantId);

        // Verify both calls used the same raw key (hashing happens inside DatabaseRateLimiter)
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(databaseRateLimiter, times(4)).check(keyCaptor.capture(), limitCaptor.capture(), any(Duration.class));
        assertEquals("ai:user:user-123", keyCaptor.getAllValues().get(0));
        assertEquals("ai:tenant:" + tenantId, keyCaptor.getAllValues().get(1));
        assertEquals("ai:user:user-123", keyCaptor.getAllValues().get(2));
        assertEquals("ai:tenant:" + tenantId, keyCaptor.getAllValues().get(3));
        assertEquals(10, limitCaptor.getAllValues().get(0));
        assertEquals(100, limitCaptor.getAllValues().get(1));
        assertEquals(10, limitCaptor.getAllValues().get(2));
        assertEquals(100, limitCaptor.getAllValues().get(3));
    }

    @Test
    void differentUsersDoNotShareCounters() {
        doNothing().when(databaseRateLimiter).check(anyString(), eq(10), any(Duration.class));
        doNothing().when(databaseRateLimiter).check(anyString(), eq(100), any(Duration.class));

        aiRateLimiter.checkAllowed("user-1", tenantId);
        aiRateLimiter.checkAllowed("user-2", tenantId);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(databaseRateLimiter, times(4)).check(keyCaptor.capture(), limitCaptor.capture(), any(Duration.class));

        assertEquals("ai:user:user-1", keyCaptor.getAllValues().get(0));
        assertEquals("ai:tenant:" + tenantId, keyCaptor.getAllValues().get(1));
        assertEquals("ai:user:user-2", keyCaptor.getAllValues().get(2));
        assertEquals("ai:tenant:" + tenantId, keyCaptor.getAllValues().get(3));
        assertEquals(10, limitCaptor.getAllValues().get(0));
        assertEquals(100, limitCaptor.getAllValues().get(1));
        assertEquals(10, limitCaptor.getAllValues().get(2));
        assertEquals(100, limitCaptor.getAllValues().get(3));
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
            if (count > limit * 2) { // user + tenant calls
                throw new RateLimitExceededException(1);
            }
            return null;
        }).when(databaseRateLimiter).check(anyString(), anyInt(), any(Duration.class));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    aiRateLimiter.checkAllowed("concurrent-user", tenantId);
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
                .when(databaseRateLimiter).check(anyString(), anyInt(), any(Duration.class));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> aiRateLimiter.checkAllowed("user-123", tenantId)
        );

        assertEquals(30, ex.retryAfterSeconds());
    }

    @Test
    void tryAcquireReturnsFalseWhenLimitExceeded() {
        when(databaseRateLimiter.tryAcquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(false);
        when(databaseRateLimiter.tryAcquire(anyString(), eq(100), any(Duration.class)))
                .thenReturn(true);

        boolean allowed = aiRateLimiter.tryAcquire("user-123", tenantId);
        assertEquals(false, allowed);
    }

    @Test
    void tryAcquireReturnsTrueWhenUnderLimit() {
        when(databaseRateLimiter.tryAcquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(true);
        when(databaseRateLimiter.tryAcquire(anyString(), eq(100), any(Duration.class)))
                .thenReturn(true);

        boolean allowed = aiRateLimiter.tryAcquire("user-123", tenantId);
        assertEquals(true, allowed);
    }

    @Test
    void tryAcquireReturnsFalseWhenTenantLimitExceeded() {
        when(databaseRateLimiter.tryAcquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(true);
        when(databaseRateLimiter.tryAcquire(anyString(), eq(100), any(Duration.class)))
                .thenReturn(false);

        boolean allowed = aiRateLimiter.tryAcquire("user-123", tenantId);
        assertEquals(false, allowed);
    }

    @Test
    void getCurrentCountReturnsZeroForUnknownUser() {
        when(databaseRateLimiter.peek(anyString(), any(Duration.class)))
                .thenReturn(0);

        int count = aiRateLimiter.getCurrentUserCount("unknown-user");
        assertEquals(0, count);
    }

    @Test
    void getCurrentCountReturnsCurrentAttempts() {
        when(databaseRateLimiter.peek(anyString(), any(Duration.class)))
                .thenReturn(7);

        int count = aiRateLimiter.getCurrentUserCount("user-123");
        assertEquals(7, count);
    }

    @Test
    void getCurrentTenantCountReturnsCurrentAttempts() {
        when(databaseRateLimiter.peek(anyString(), any(Duration.class)))
                .thenReturn(50);

        int count = aiRateLimiter.getCurrentTenantCount(tenantId);
        assertEquals(50, count);
    }

    @Test
    void resetUserRemovesUserCounter() {
        aiRateLimiter.resetUser("user-123");

        // DatabaseRateLimiter.reset receives the raw key and hashes it internally
        verify(databaseRateLimiter).reset(eq("ai:user:user-123"));
    }

    @Test
    void resetTenantRemovesTenantCounter() {
        aiRateLimiter.resetTenant(tenantId);

        verify(databaseRateLimiter).reset(eq("ai:tenant:" + tenantId));
    }

    @Test
    void usesCorrectWindowFromConfiguration() {
        aiProperties.getRateLimit().setMaxRequests(5);
        aiProperties.getRateLimit().setWindowMillis(120_000L); // 2 minutes
        aiProperties.getRateLimit().setTenantMaxRequests(50);
        aiProperties.getRateLimit().setTenantWindowMillis(120_000L);

        doNothing().when(databaseRateLimiter).check(anyString(), eq(5), any(Duration.class));
        doNothing().when(databaseRateLimiter).check(anyString(), eq(50), any(Duration.class));

        aiRateLimiter.checkAllowed("user-123", tenantId);

        verify(databaseRateLimiter).check(anyString(), eq(5), any(Duration.class));
        verify(databaseRateLimiter).check(anyString(), eq(50), any(Duration.class));
    }
}