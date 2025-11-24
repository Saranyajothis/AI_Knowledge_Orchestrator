package com.aiko.orchestrator.service;

import com.aiko.orchestrator.annotation.RateLimit;
import com.aiko.orchestrator.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Service for managing rate limiting using Bucket4j with Redis backend
 * Provides distributed rate limiting across multiple instances
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    
    private final RedisConnectionFactory redisConnectionFactory;
    private ProxyManager<String> proxyManager;
    
    // Cache for bucket configurations
    private final Map<String, Supplier<BucketConfiguration>> configurationCache = new ConcurrentHashMap<>();
    
    @Value("${ratelimit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${ratelimit.default.capacity:100}")
    private long defaultCapacity;
    
    @Value("${ratelimit.default.refill-tokens:100}")
    private long defaultRefillTokens;
    
    @Value("${ratelimit.default.refill-period-seconds:60}")
    private long defaultRefillPeriodSeconds;
    
    @PostConstruct
    public void init() {
        if (rateLimitEnabled) {
            log.info("Initializing Rate Limit Service with Redis backend");
            
            // Create Redis client for Bucket4j
            RedisClient redisClient = RedisClient.create("redis://localhost:6379");
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            );
            
            // Create ProxyManager for distributed rate limiting
            this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                    .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                            Duration.ofSeconds(defaultRefillPeriodSeconds * 10)
                        )
                    )
                    .build();
            
            log.info("Rate Limit Service initialized successfully");
        } else {
            log.warn("Rate limiting is disabled");
        }
    }
    
    /**
     * Check if request should be rate limited
     * Returns true if request is allowed, false if rate limited
     */
    public boolean tryConsume(String key, RateLimit rateLimit) {
        if (!rateLimitEnabled) {
            return true; // Rate limiting disabled, allow all requests
        }
        
        try {
            Bucket bucket = getBucket(key, rateLimit);
            boolean consumed = bucket.tryConsume(1);
            
            if (consumed) {
                log.debug("Rate limit token consumed for key: {}", key);
            } else {
                log.warn("Rate limit exceeded for key: {}", key);
            }
            
            return consumed;
        } catch (Exception e) {
            log.error("Error checking rate limit for key {}: {}", key, e.getMessage());
            // On error, allow request to prevent blocking users
            return true;
        }
    }
    
    /**
     * Check if request should be rate limited and throw exception if exceeded
     */
    public void checkRateLimit(String key, RateLimit rateLimit) {
        if (!tryConsume(key, rateLimit)) {
            long retryAfter = getRetryAfterSeconds(key, rateLimit);
            throw new RateLimitExceededException(
                rateLimit.errorMessage(),
                key,
                retryAfter,
                key
            );
        }
    }
    
    /**
     * Get or create a bucket for the given key
     */
    private Bucket getBucket(String key, RateLimit rateLimit) {
        String bucketKey = "rate_limit:" + key;
        
        // Get or create bucket configuration
        Supplier<BucketConfiguration> configSupplier = configurationCache.computeIfAbsent(
            getCacheKey(rateLimit),
            k -> () -> createBucketConfiguration(rateLimit)
        );
        
        // Get or create bucket from Redis
        return proxyManager.builder()
                .build(bucketKey, configSupplier);
    }
    
    /**
     * Create bucket configuration from rate limit annotation
     */
    private BucketConfiguration createBucketConfiguration(RateLimit rateLimit) {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : defaultCapacity;
        long refillTokens = rateLimit.refillTokens() > 0 ? rateLimit.refillTokens() : defaultRefillTokens;
        long refillPeriod = rateLimit.refillPeriodSeconds() > 0 ? 
                           rateLimit.refillPeriodSeconds() : defaultRefillPeriodSeconds;
        
        Refill refill = Refill.intervally(refillTokens, Duration.ofSeconds(refillPeriod));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        
        log.debug("Creating bucket configuration: capacity={}, refill={}/{} seconds", 
                 capacity, refillTokens, refillPeriod);
        
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
    
    /**
     * Get retry-after time in seconds
     */
    private long getRetryAfterSeconds(String key, RateLimit rateLimit) {
        try {
            Bucket bucket = getBucket(key, rateLimit);
            long nanosToWait = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            return Math.max(1, nanosToWait / 1_000_000_000L); // Convert nanos to seconds
        } catch (Exception e) {
            log.error("Error calculating retry-after for key {}: {}", key, e.getMessage());
            return rateLimit.refillPeriodSeconds();
        }
    }
    
    /**
     * Generate rate limit key based on request and configuration
     */
    public String generateKey(HttpServletRequest request, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        
        switch (rateLimit.keyType()) {
            case IP:
                keyBuilder.append("ip:").append(getClientIp(request));
                break;
                
            case USER:
                String userId = request.getHeader("X-User-Id");
                if (userId == null) {
                    userId = "anonymous";
                }
                keyBuilder.append("user:").append(userId);
                break;
                
            case API_KEY:
                String apiKey = request.getHeader("X-API-Key");
                if (apiKey == null) {
                    apiKey = "no-api-key";
                }
                keyBuilder.append("api:").append(apiKey);
                break;
                
            case SESSION:
                String sessionId = request.getSession(false) != null ? 
                                  request.getSession().getId() : "no-session";
                keyBuilder.append("session:").append(sessionId);
                break;
                
            case GLOBAL:
                keyBuilder.append("global");
                break;
                
            case CUSTOM:
                if (rateLimit.customKey() != null && !rateLimit.customKey().isEmpty()) {
                    keyBuilder.append("custom:").append(evaluateCustomKey(request, rateLimit.customKey()));
                } else {
                    keyBuilder.append("custom:default");
                }
                break;
                
            default:
                keyBuilder.append("default:").append(getClientIp(request));
        }
        
        // Optionally include path in key
        if (rateLimit.includePathInKey()) {
            keyBuilder.append(":").append(request.getRequestURI());
        }
        
        String key = keyBuilder.toString();
        log.debug("Generated rate limit key: {}", key);
        return key;
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "X-Real-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Evaluate custom key expression
     * TODO: Implement SpEL evaluation if needed
     */
    private String evaluateCustomKey(HttpServletRequest request, String expression) {
        // For now, just return the expression as-is
        // In a real implementation, you'd use SpEL to evaluate the expression
        return expression;
    }
    
    /**
     * Generate cache key for configuration
     */
    private String getCacheKey(RateLimit rateLimit) {
        return String.format("%d:%d:%d", 
            rateLimit.capacity(), 
            rateLimit.refillTokens(), 
            rateLimit.refillPeriodSeconds());
    }
    
    /**
     * Get current rate limit status for a key
     */
    public RateLimitStatus getStatus(String key, RateLimit rateLimit) {
        if (!rateLimitEnabled) {
            return new RateLimitStatus(true, defaultCapacity, defaultCapacity, 0);
        }
        
        try {
            Bucket bucket = getBucket(key, rateLimit);
            long availableTokens = bucket.getAvailableTokens();
            long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : defaultCapacity;
            
            return new RateLimitStatus(
                availableTokens > 0,
                availableTokens,
                capacity,
                availableTokens > 0 ? 0 : getRetryAfterSeconds(key, rateLimit)
            );
        } catch (Exception e) {
            log.error("Error getting rate limit status for key {}: {}", key, e.getMessage());
            return new RateLimitStatus(true, defaultCapacity, defaultCapacity, 0);
        }
    }
    
    /**
     * Reset rate limit for a specific key
     */
    public void resetRateLimit(String key) {
        if (!rateLimitEnabled) {
            return;
        }
        
        try {
            String bucketKey = "rate_limit:" + key;
            proxyManager.removeProxy(bucketKey);
            log.info("Reset rate limit for key: {}", key);
        } catch (Exception e) {
            log.error("Error resetting rate limit for key {}: {}", key, e.getMessage());
        }
    }
    
    /**
     * Rate limit status DTO
     */
    public static class RateLimitStatus {
        private final boolean allowed;
        private final long remainingTokens;
        private final long capacity;
        private final long retryAfterSeconds;
        
        public RateLimitStatus(boolean allowed, long remainingTokens, long capacity, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.capacity = capacity;
            this.retryAfterSeconds = retryAfterSeconds;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public long getRemainingTokens() {
            return remainingTokens;
        }
        
        public long getCapacity() {
            return capacity;
        }
        
        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
