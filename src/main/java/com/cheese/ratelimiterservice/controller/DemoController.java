package com.cheese.ratelimiterservice.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @PostMapping("/login")
    public Map<String, Object> login() {
        return Map.of("status", "success");
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String query) {
        return Map.of("query", query, "status", "success");
    }
}
