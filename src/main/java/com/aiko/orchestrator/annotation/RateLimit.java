package com.aiko.orchestrator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to apply rate limiting to methods or classes.
 * Can be used at method level or class level.
 * 
 * Example usage:
 * @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
 * public ResponseEntity<?> myEndpoint() { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * Maximum number of requests allowed in the bucket
     * Default: 20 requests
     */
    long capacity() default 20;
    
    /**
     * Number of tokens to refill
     * Default: 20 tokens
     */
    long refillTokens() default 20;
    
    /**
     * Period in seconds for token refill
     * Default: 60 seconds (1 minute)
     */
    long refillPeriodSeconds() default 60;
    
    /**
     * Rate limit key strategy
     * Default: IP-based rate limiting
     */
    RateLimitKeyType keyType() default RateLimitKeyType.IP;
    
    /**
     * Custom rate limit key (optional)
     * Use SpEL expressions like #userId or #request.getHeader('X-API-Key')
     */
    String customKey() default "";
    
    /**
     * Whether to include path in the rate limit key
     * Useful for having different limits per endpoint
     */
    boolean includePathInKey() default false;
    
    /**
     * Error message when rate limit is exceeded
     */
    String errorMessage() default "Too many requests. Please try again later.";
    
    /**
     * HTTP status code to return when rate limited
     * Default: 429 (Too Many Requests)
     */
    int httpStatusCode() default 429;
    
    /**
     * Key type for rate limiting
     */
    enum RateLimitKeyType {
        IP,           // Rate limit by IP address
        USER,         // Rate limit by authenticated user
        API_KEY,      // Rate limit by API key
        SESSION,      // Rate limit by session ID
        GLOBAL,       // Global rate limit (same for all requests)
        CUSTOM        // Custom key using SpEL expression
    }
}
