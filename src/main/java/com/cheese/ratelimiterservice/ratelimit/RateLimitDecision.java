package com.cheese.ratelimiterservice.ratelimit;

public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterSeconds, long resetSeconds) {

    public static RateLimitDecision allow(long remainingTokens, long resetSeconds) {
        return new RateLimitDecision(true, remainingTokens, 0, resetSeconds);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, retryAfterSeconds, retryAfterSeconds);
    }
}
