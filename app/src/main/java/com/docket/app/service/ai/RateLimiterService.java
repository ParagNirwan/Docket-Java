package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final DocketAiProperties aiProperties;
    private final ConcurrentHashMap<UUID, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimiterService() {
        this(new DocketAiProperties());
    }

    public boolean isAllowed(UUID userId) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        long windowMillis = aiProperties.getRateLimit().getWindowMillis();
        // Evict expired entries
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= aiProperties.getRateLimit().getMaxRequests()) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}
