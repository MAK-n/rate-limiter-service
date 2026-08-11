package com.cheese.ratelimiterservice.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cheese.ratelimiterservice.redis.RedisRateLimiterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private final RedisRateLimiterRepository redisRateLimiterRepository;

    @Value("${ratelimit.capacity}")
    private long capacity;
    @Value("${ratelimit.refillRate}")
    private double refillRate;


    public RateLimitDecision tryAcquire(String key) {
        return redisRateLimiterRepository.tryAcquire(key, capacity, refillRate);
    }
}
