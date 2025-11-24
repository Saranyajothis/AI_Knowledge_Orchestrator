package com.aiko.orchestrator.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for rate limiting
 * Defines rate limit profiles and settings
 */
@Configuration
public class RateLimitConfig {
    
    @Value("${ratelimit.enabled:true}")
    private boolean enabled;
    
    @Value("${ratelimit.redis.key-prefix:rate_limit}")
    private String redisKeyPrefix;
    
    // Standard rate limit profiles
    public static final class Profiles {
        
        // Very restrictive - for sensitive operations
        public static final class STRICT {
            public static final long CAPACITY = 5;
            public static final long REFILL_TOKENS = 5;
            public static final long REFILL_PERIOD_SECONDS = 60;
        }
        
        // Standard API rate limit
        public static final class STANDARD {
            public static final long CAPACITY = 100;
            public static final long REFILL_TOKENS = 100;
            public static final long REFILL_PERIOD_SECONDS = 60;
        }
        
        // Relaxed rate limit for read operations
        public static final class RELAXED {
            public static final long CAPACITY = 1000;
            public static final long REFILL_TOKENS = 1000;
            public static final long REFILL_PERIOD_SECONDS = 60;
        }
        
        // For authenticated/premium users
        public static final class PREMIUM {
            public static final long CAPACITY = 5000;
            public static final long REFILL_TOKENS = 5000;
            public static final long REFILL_PERIOD_SECONDS = 60;
        }
        
        // For internal/admin operations
        public static final class UNLIMITED {
            public static final long CAPACITY = Long.MAX_VALUE;
            public static final long REFILL_TOKENS = Long.MAX_VALUE;
            public static final long REFILL_PERIOD_SECONDS = 1;
        }
    }
    
    /**
     * Get rate limit configuration by endpoint pattern
     * This can be used for dynamic rate limit configuration
     */
    public Map<String, RateLimitSettings> getEndpointConfigs() {
        Map<String, RateLimitSettings> configs = new HashMap<>();
        
        // Query endpoints - standard rate limit
        configs.put("/api/v1/queries/**", new RateLimitSettings(
            Profiles.STANDARD.CAPACITY,
            Profiles.STANDARD.REFILL_TOKENS,
            Profiles.STANDARD.REFILL_PERIOD_SECONDS
        ));
        
        // Knowledge search - relaxed for read operations
        configs.put("/api/v1/knowledge/search", new RateLimitSettings(
            Profiles.RELAXED.CAPACITY,
            Profiles.RELAXED.REFILL_TOKENS,
            Profiles.RELAXED.REFILL_PERIOD_SECONDS
        ));
        
        // Document upload - strict to prevent abuse
        configs.put("/api/v1/knowledge/upload", new RateLimitSettings(
            Profiles.STRICT.CAPACITY,
            Profiles.STRICT.REFILL_TOKENS,
            Profiles.STRICT.REFILL_PERIOD_SECONDS
        ));
        
        // AI operations - strict due to cost
        configs.put("/api/v1/ai/**", new RateLimitSettings(
            10, // 10 requests
            10,
            60  // per minute
        ));
        
        // Health check - unlimited
        configs.put("/api/v1/health/**", new RateLimitSettings(
            Profiles.UNLIMITED.CAPACITY,
            Profiles.UNLIMITED.REFILL_TOKENS,
            Profiles.UNLIMITED.REFILL_PERIOD_SECONDS
        ));
        
        // Admin endpoints - unlimited for admins
        configs.put("/api/v1/admin/**", new RateLimitSettings(
            Profiles.UNLIMITED.CAPACITY,
            Profiles.UNLIMITED.REFILL_TOKENS,
            Profiles.UNLIMITED.REFILL_PERIOD_SECONDS
        ));
        
        return configs;
    }
    
    /**
     * Rate limit settings DTO
     */
    public static class RateLimitSettings {
        private final long capacity;
        private final long refillTokens;
        private final long refillPeriodSeconds;
        
        public RateLimitSettings(long capacity, long refillTokens, long refillPeriodSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodSeconds = refillPeriodSeconds;
        }
        
        public long getCapacity() {
            return capacity;
        }
        
        public long getRefillTokens() {
            return refillTokens;
        }
        
        public long getRefillPeriodSeconds() {
            return refillPeriodSeconds;
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }
}
