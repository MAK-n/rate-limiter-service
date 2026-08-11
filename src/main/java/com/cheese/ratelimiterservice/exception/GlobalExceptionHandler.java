package com.cheese.ratelimiterservice.exception;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceededException(RateLimitExceededException ex) {
        
        return ResponseEntity.status(429)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .header("X-RateLimit-Reset", String.valueOf(ex.getResetSeconds()))
        .body(Map.of("status", 429,
         "error", "Too Many Requests",
         "message", "You have exceeded the rate limit. Please try again later.",
         "retryAfterSeconds", ex.getRetryAfterSeconds()));
    }
}
