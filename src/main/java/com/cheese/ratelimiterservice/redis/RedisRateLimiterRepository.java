package com.cheese.ratelimiterservice.redis;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.cheese.ratelimiterservice.ratelimit.RateLimitDecision;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisRateLimiterRepository {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List<Long>> tokenBucketScript;

    public RateLimitDecision tryAcquire(String key, long capacity, double refillRate) {
        long bucketTtlSeconds = Math.max(1, (long) Math.ceil(capacity / refillRate));
        List<Long> result = redisTemplate.execute(
            tokenBucketScript,
            List.of(key),
            String.valueOf(capacity),
            String.valueOf(refillRate),
            String.valueOf(bucketTtlSeconds)
        );

        long allowed = result.get(0);
        long retryAfterSeconds = result.get(2);
        return allowed == 1 ? RateLimitDecision.allow() : RateLimitDecision.denied(retryAfterSeconds);
    }
}
