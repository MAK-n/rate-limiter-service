package com.cheese.ratelimiterservice.interceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.cheese.ratelimiterservice.annotation.RateLimit;
import com.cheese.ratelimiterservice.exception.RateLimitExceededException;
import com.cheese.ratelimiterservice.ratelimit.RateLimitDecision;
import com.cheese.ratelimiterservice.ratelimit.RateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor{
    
    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if(rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        
        String key = resolveKey(request);
        RateLimitDecision decision = ( rateLimit != null )
        ? rateLimiterService.tryAcquire(key, rateLimit.capacity(), rateLimit.capacity() / (double) rateLimit.window())
        : rateLimiterService.tryAcquireDefault(key);

        if(!decision.allowed()) {
            log.warn("Throttled {} {} for key={}, retryAfterSeconds={}",
                request.getMethod(), request.getRequestURI(), key, decision.retryAfterSeconds());
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));
        return true;
    }

    private String resolveKey(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if(userId != null && !userId.isEmpty()) {
            return "user:" + userId + ":" + request.getMethod() + ":" + request.getRequestURI();
        }
        return "ip:" + request.getRemoteAddr() + ":" + request.getMethod() + ":" + request.getRequestURI(); 
    }
}
