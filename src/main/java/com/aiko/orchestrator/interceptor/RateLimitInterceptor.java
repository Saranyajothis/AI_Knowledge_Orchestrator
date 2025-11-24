package com.aiko.orchestrator.interceptor;

import com.aiko.orchestrator.annotation.RateLimit;
import com.aiko.orchestrator.exception.RateLimitExceededException;
import com.aiko.orchestrator.service.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that applies rate limiting to requests based on @RateLimit annotation
 * Checks both method-level and class-level annotations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimitService rateLimitService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        // Only apply rate limiting to controller methods
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // Check for rate limit annotation on method
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        
        // If not on method, check on class
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        
        // If no rate limit annotation, allow request
        if (rateLimit == null) {
            return true;
        }
        
        // Generate rate limit key
        String key = rateLimitService.generateKey(request, rateLimit);
        
        // Check rate limit
        try {
            rateLimitService.checkRateLimit(key, rateLimit);
            
            // Add rate limit headers to response
            addRateLimitHeaders(response, key, rateLimit);
            
            return true; // Request allowed
            
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for key: {} - {}", key, e.getMessage());
            
            // Set response status and headers
            response.setStatus(rateLimit.httpStatusCode());
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.capacity()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            
            // Write error response
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"%s\",\"retryAfter\":%d}",
                e.getMessage(),
                e.getRetryAfterSeconds()
            ));
            
            return false; // Request blocked
        }
    }
    
    /**
     * Add rate limit headers to response for transparency
     */
    private void addRateLimitHeaders(HttpServletResponse response, String key, RateLimit rateLimit) {
        try {
            RateLimitService.RateLimitStatus status = rateLimitService.getStatus(key, rateLimit);
            
            response.setHeader("X-RateLimit-Limit", String.valueOf(status.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(status.getRemainingTokens()));
            
            if (status.getRetryAfterSeconds() > 0) {
                response.setHeader("X-RateLimit-Retry-After", String.valueOf(status.getRetryAfterSeconds()));
            }
        } catch (Exception e) {
            log.debug("Error adding rate limit headers: {}", e.getMessage());
        }
    }
}
