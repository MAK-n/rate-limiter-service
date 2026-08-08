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
        refill();
        if (tokens >= 1) {
            tokens--;
            return true;
        }
        return false;
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