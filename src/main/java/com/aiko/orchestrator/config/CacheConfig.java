package com.aiko.orchestrator.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration for Spring Cache with Redis
 * Enables caching across the application with Redis as the cache store
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    
    /**
     * Configure Redis Cache Manager with custom settings per cache
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("Configuring Redis Cache Manager");
        
        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60)) // Default TTL: 60 minutes
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        // Custom cache configurations for different cache regions
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Query cache - shorter TTL as queries change frequently
        cacheConfigurations.put("queries", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(15))
                .prefixCacheNameWith("query::"));
        
        // Query history cache - longer TTL as history doesn't change
        cacheConfigurations.put("queryHistory", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("history::"));
        
        // Knowledge/Document cache - longer TTL as documents are relatively static
        cacheConfigurations.put("documents", defaultCacheConfig
                .entryTtl(Duration.ofHours(2))
                .prefixCacheNameWith("doc::"));
        
        // Knowledge search cache - medium TTL
        cacheConfigurations.put("knowledgeSearch", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("search::"));
        
        // Response cache - medium TTL
        cacheConfigurations.put("responses", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("response::"));
        
        // User cache - for user-specific data
        cacheConfigurations.put("users", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(45))
                .prefixCacheNameWith("user::"));
        
        // Statistics cache - very short TTL for real-time stats
        cacheConfigurations.put("statistics", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(5))
                .prefixCacheNameWith("stats::"));
        
        // AI responses cache - cache AI-generated content
        cacheConfigurations.put("aiResponses", defaultCacheConfig
                .entryTtl(Duration.ofHours(1))
                .prefixCacheNameWith("ai::"));
        
        // Build the cache manager
        RedisCacheManager cacheManager = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
        
        log.info("Redis Cache Manager configured with {} cache regions", cacheConfigurations.size());
        
        return cacheManager;
    }
    
    /**
     * Custom key generator for complex cache keys
     * This is used when @Cacheable doesn't specify a key
     */
    @Override
    @Bean
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(".");
            sb.append(method.getName()).append(":");
            
            if (params.length == 0) {
                sb.append("noparams");
            } else {
                for (Object param : params) {
                    if (param != null) {
                        sb.append(param.toString()).append(",");
                    }
                }
                // Remove trailing comma
                if (sb.charAt(sb.length() - 1) == ',') {
                    sb.deleteCharAt(sb.length() - 1);
                }
            }
            
            String key = sb.toString();
            log.debug("Generated cache key: {}", key);
            return key;
        };
    }
    
    /**
     * Custom cache resolver if needed
     */
    @Override
    public CacheResolver cacheResolver() {
        return null; // Use default
    }
    
    /**
     * Custom error handler for cache operations
     * This prevents cache errors from breaking the application
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, 
                                           org.springframework.cache.Cache cache, Object key) {
                log.error("Cache get error for cache: {} key: {}", cache.getName(), key, exception);
            }
            
            @Override
            public void handleCachePutError(RuntimeException exception, 
                                           org.springframework.cache.Cache cache, 
                                           Object key, Object value) {
                log.error("Cache put error for cache: {} key: {}", cache.getName(), key, exception);
            }
            
            @Override
            public void handleCacheEvictError(RuntimeException exception, 
                                            org.springframework.cache.Cache cache, Object key) {
                log.error("Cache evict error for cache: {} key: {}", cache.getName(), key, exception);
            }
            
            @Override
            public void handleCacheClearError(RuntimeException exception, 
                                            org.springframework.cache.Cache cache) {
                log.error("Cache clear error for cache: {}", cache.getName(), exception);
            }
        };
    }
}
