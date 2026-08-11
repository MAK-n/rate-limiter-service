package com.cheese.ratelimiterservice.exception;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    private final long resetSeconds;

    public RateLimitExceededException(long retryAfterSeconds, long resetSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
        this.resetSeconds = resetSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getResetSeconds() {
        return resetSeconds;
    }
}
