package com.cheese.ratelimiterservice.ratelimit;

public class TokenBucket {
    private final long capacity;
    private final double refillRate;
    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucket(long capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume() {
        return consume().allowed();
    }

    /**
     * Refills and attempts to consume a token in one synchronized step, so the
     * check-then-decrement can't race with a concurrent call on the same bucket.
     */
    public synchronized RateLimitDecision consume() {
        refill();
        if (tokens >= 1) {
            tokens--;
            return RateLimitDecision.allow();
        }
        long retryAfterSeconds = (long) Math.ceil((1 - tokens) / refillRate);
        return RateLimitDecision.denied(Math.max(1, retryAfterSeconds));
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedTime = ( now - lastRefillTimestamp ) / 1000;
        if (elapsedTime > 0) {
            double newTokens = elapsedTime * refillRate;
            tokens = Math.min(capacity, tokens + newTokens);
            lastRefillTimestamp = now;
        }
    }

    public synchronized long getAvailableTokens() {
        refill();
        return (long) tokens;
    }
}