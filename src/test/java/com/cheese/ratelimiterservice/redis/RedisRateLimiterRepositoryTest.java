package com.cheese.ratelimiterservice.redis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.cheese.ratelimiterservice.ratelimit.RateLimitDecision;

@Testcontainers
@SpringBootTest
public class RedisRateLimiterRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisRateLimiterRepository repository;

    @Test
    void allowsUpToCapacityThenDenies() {
        String key = "testt" + UUID.randomUUID();
        for(int i = 0; i < 10; i++) {
            assertTrue(repository.tryAcquire(key, 10L, 1.0).allowed());
        }
        RateLimitDecision decision = repository.tryAcquire(key, 10L, 1.0);
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() >= 1);
    }
}
