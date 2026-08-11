package com.cheese.ratelimiterservice.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    @SuppressWarnings("unchecked")
    @Bean
    public DefaultRedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("token_bucket.lua"));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }
}
