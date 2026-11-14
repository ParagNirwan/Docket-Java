package com.docket.app.service.ai;

public class RateLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}