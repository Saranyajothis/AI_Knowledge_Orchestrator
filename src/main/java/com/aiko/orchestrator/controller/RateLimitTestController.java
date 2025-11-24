package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.annotation.RateLimit;
import com.aiko.orchestrator.service.RateLimitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Controller for testing and managing rate limiting
 * Demonstrates different rate limiting configurations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
public class RateLimitTestController {
    
    private final RateLimitService rateLimitService;
    
    /**
     * Test endpoint with standard rate limiting
     * 100 requests per minute
     */
    @GetMapping("/test/standard")
    @RateLimit(capacity = 100, refillTokens = 100, refillPeriodSeconds = 60)
    public ResponseEntity<Map<String, Object>> testStandardRateLimit(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Standard rate limit test successful");
        response.put("endpoint", "/test/standard");
        response.put("limit", "100 requests per minute");
        response.put("timestamp", new Date());
        response.put("clientIp", request.getRemoteAddr());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test endpoint with strict rate limiting
     * 5 requests per minute
     */
    @GetMapping("/test/strict")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60,
              errorMessage = "Strict rate limit exceeded. Maximum 5 requests per minute allowed.")
    public ResponseEntity<Map<String, Object>> testStrictRateLimit() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Strict rate limit test successful");
        response.put("endpoint", "/test/strict");
        response.put("limit", "5 requests per minute");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test endpoint with relaxed rate limiting
     * 1000 requests per minute
     */
    @GetMapping("/test/relaxed")
    @RateLimit(capacity = 1000, refillTokens = 1000, refillPeriodSeconds = 60)
    public ResponseEntity<Map<String, Object>> testRelaxedRateLimit() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Relaxed rate limit test successful");
        response.put("endpoint", "/test/relaxed");
        response.put("limit", "1000 requests per minute");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test user-based rate limiting
     * Requires X-User-Id header
     */
    @GetMapping("/test/user")
    @RateLimit(capacity = 50, refillTokens = 50, refillPeriodSeconds = 60,
              keyType = RateLimit.RateLimitKeyType.USER)
    public ResponseEntity<Map<String, Object>> testUserRateLimit(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User-based rate limit test successful");
        response.put("endpoint", "/test/user");
        response.put("userId", userId != null ? userId : "anonymous");
        response.put("limit", "50 requests per minute per user");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test API key-based rate limiting
     * Requires X-API-Key header
     */
    @GetMapping("/test/api-key")
    @RateLimit(capacity = 200, refillTokens = 200, refillPeriodSeconds = 60,
              keyType = RateLimit.RateLimitKeyType.API_KEY)
    public ResponseEntity<Map<String, Object>> testApiKeyRateLimit(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "API key-based rate limit test successful");
        response.put("endpoint", "/test/api-key");
        response.put("apiKey", apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "none");
        response.put("limit", "200 requests per minute per API key");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test global rate limiting
     * Same limit for all requests
     */
    @GetMapping("/test/global")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60,
              keyType = RateLimit.RateLimitKeyType.GLOBAL)
    public ResponseEntity<Map<String, Object>> testGlobalRateLimit() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Global rate limit test successful");
        response.put("endpoint", "/test/global");
        response.put("limit", "30 requests per minute globally");
        response.put("timestamp", new Date());
        response.put("note", "This limit is shared across all users");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test path-specific rate limiting
     */
    @GetMapping("/test/path/{id}")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60,
              includePathInKey = true)
    public ResponseEntity<Map<String, Object>> testPathRateLimit(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Path-specific rate limit test successful");
        response.put("endpoint", "/test/path/" + id);
        response.put("pathId", id);
        response.put("limit", "20 requests per minute per path");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get rate limit status for current client
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userId = request.getHeader("X-User-Id");
        
        Map<String, Object> status = new HashMap<>();
        status.put("clientIp", clientIp);
        status.put("userId", userId);
        
        // Create a default rate limit for status check
        RateLimit defaultLimit = new RateLimit() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimit.class;
            }
            @Override
            public long capacity() { return 100; }
            @Override
            public long refillTokens() { return 100; }
            @Override
            public long refillPeriodSeconds() { return 60; }
            @Override
            public RateLimit.RateLimitKeyType keyType() { return RateLimit.RateLimitKeyType.IP; }
            @Override
            public String customKey() { return ""; }
            @Override
            public boolean includePathInKey() { return false; }
            @Override
            public String errorMessage() { return "Rate limit exceeded"; }
            @Override
            public int httpStatusCode() { return 429; }
        };
        
        String key = rateLimitService.generateKey(request, defaultLimit);
        RateLimitService.RateLimitStatus rateLimitStatus = rateLimitService.getStatus(key, defaultLimit);
        
        status.put("rateLimitKey", key);
        status.put("allowed", rateLimitStatus.isAllowed());
        status.put("remainingTokens", rateLimitStatus.getRemainingTokens());
        status.put("capacity", rateLimitStatus.getCapacity());
        status.put("percentageRemaining", 
                  (double) rateLimitStatus.getRemainingTokens() / rateLimitStatus.getCapacity() * 100);
        
        if (!rateLimitStatus.isAllowed()) {
            status.put("retryAfterSeconds", rateLimitStatus.getRetryAfterSeconds());
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Reset rate limit for a specific key (admin only)
     */
    @DeleteMapping("/reset/{key}")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@PathVariable String key) {
        rateLimitService.resetRateLimit(key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Rate limit reset successfully");
        response.put("key", key);
        response.put("timestamp", new Date());
        
        log.info("Rate limit reset for key: {}", key);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test burst requests to demonstrate rate limiting
     */
    @PostMapping("/test/burst")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    public ResponseEntity<Map<String, Object>> testBurstRequests(
            @RequestParam(defaultValue = "5") int count) {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (int i = 1; i <= count; i++) {
            Map<String, Object> attempt = new HashMap<>();
            attempt.put("attemptNumber", i);
            attempt.put("timestamp", System.currentTimeMillis());
            attempt.put("success", true);
            results.add(attempt);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Burst test completed");
        response.put("requestedCount", count);
        response.put("results", results);
        response.put("note", "If you see this, all requests succeeded. Try with more than 10 requests.");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint without rate limiting for comparison
     */
    @GetMapping("/test/unlimited")
    public ResponseEntity<Map<String, Object>> testUnlimited() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "No rate limit applied to this endpoint");
        response.put("endpoint", "/test/unlimited");
        response.put("timestamp", new Date());
        response.put("note", "This endpoint has no @RateLimit annotation");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "X-Real-IP"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
