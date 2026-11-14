package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "docket.webhooks")
public class DocketWebhookProperties {

    public static final int DEFAULT_MAX_RETRIES = 5;
    public static final long DEFAULT_RETRY_DELAY_MS = 60_000L; // 1 minute
    public static final long DEFAULT_MAX_RETRY_DELAY_MS = 3_600_000L; // 1 hour
    public static final int DEFAULT_HTTP_TIMEOUT_MS = 10_000; // 10 seconds
    public static final int DEFAULT_BATCH_SIZE = 100;

    private int maxRetries = DEFAULT_MAX_RETRIES;
    private long retryDelayMs = DEFAULT_RETRY_DELAY_MS;
    private long maxRetryDelayMs = DEFAULT_MAX_RETRY_DELAY_MS;
    private int httpTimeoutMs = DEFAULT_HTTP_TIMEOUT_MS;
    private int batchSize = DEFAULT_BATCH_SIZE;

    public Duration getRetryDelay() {
        return Duration.ofMillis(retryDelayMs);
    }

    public Duration getMaxRetryDelay() {
        return Duration.ofMillis(maxRetryDelayMs);
    }

    public Duration getHttpTimeout() {
        return Duration.ofMillis(httpTimeoutMs);
    }
}