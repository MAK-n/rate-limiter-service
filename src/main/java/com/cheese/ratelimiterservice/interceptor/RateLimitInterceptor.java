package com.cheese.ratelimiterservice.interceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.cheese.ratelimiterservice.ratelimit.RateLimitDecision;
import com.cheese.ratelimiterservice.ratelimit.RateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor{
    
    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = resolveKey(request);
        RateLimitDecision decision = rateLimiterService.tryAcquire(key);
        
        if(decision.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if(userId != null && !userId.isEmpty()) {
            return "user: " + userId;
        }
        return "ip: " + request.getRemoteAddr();
    }
}
