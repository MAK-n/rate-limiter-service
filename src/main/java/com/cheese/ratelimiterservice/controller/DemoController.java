package com.cheese.ratelimiterservice.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cheese.ratelimiterservice.annotation.RateLimit;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @RateLimit(capacity = 5, window = 60) // 5 requests per minute
    @PostMapping("/login")
    public Map<String, Object> login() {
        return Map.of("status", "success");
    }

    @RateLimit(capacity = 20, window = 60) // 20 requests per minute
    @PostMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String query) {
        return Map.of("query", query, "status", "success");
    }
}
