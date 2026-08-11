package com.cheese.ratelimiterservice.ratelimit;

public record RateLimitDecision(boolean allowed,long remainingTokens, long retryAfterSeconds) {

    public static RateLimitDecision allow(long remainingTokens) {
        return new RateLimitDecision(true, remainingTokens, 0);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, retryAfterSeconds);
    }
}
