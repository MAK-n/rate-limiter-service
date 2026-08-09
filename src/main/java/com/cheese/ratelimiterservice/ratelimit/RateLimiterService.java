package com.cheese.ratelimiterservice.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {
    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();

    private final long capacity;
    private final double refillRate;

    public RateLimiterService(@Value("${ratelimit.capacity}") long capacity, @Value("${ratelimit.refillRate}") double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    public RateLimitDecision tryAcquire(String key) {
        TokenBucket bucket = tokenBuckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRate));
        return bucket.consume();
    }
}
