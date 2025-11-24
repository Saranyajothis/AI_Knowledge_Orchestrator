package com.aiko.orchestrator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for managing and monitoring cache operations
 * Provides endpoints to view cache statistics, clear caches, and test caching
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {
    
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Get information about all available caches
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getCacheInfo() {
        Map<String, Object> cacheInfo = new HashMap<>();
        
        Collection<String> cacheNames = cacheManager.getCacheNames();
        cacheInfo.put("totalCaches", cacheNames.size());
        cacheInfo.put("cacheNames", cacheNames);
        
        Map<String, Object> cacheDetails = new HashMap<>();
        for (String cacheName : cacheNames) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("name", cacheName);
                details.put("type", cache.getClass().getSimpleName());
                
                // Try to get cache statistics if available
                Object nativeCache = cache.getNativeCache();
                if (nativeCache != null) {
                    details.put("nativeType", nativeCache.getClass().getSimpleName());
                }
                
                cacheDetails.put(cacheName, details);
            }
        }
        
        cacheInfo.put("cacheDetails", cacheDetails);
        
        return ResponseEntity.ok(cacheInfo);
    }
    
    /**
     * Clear a specific cache
     */
    @DeleteMapping("/clear/{cacheName}")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }
        
        cache.clear();
        log.info("Cleared cache: {}", cacheName);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cache cleared successfully");
        response.put("cacheName", cacheName);
        response.put("timestamp", new Date().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clear all caches
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        int clearedCount = 0;
        
        for (String cacheName : cacheNames) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                clearedCount++;
                log.info("Cleared cache: {}", cacheName);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "All caches cleared successfully");
        response.put("clearedCount", clearedCount);
        response.put("caches", cacheNames);
        response.put("timestamp", new Date().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Evict a specific key from a cache
     */
    @DeleteMapping("/evict/{cacheName}/{key}")
    public ResponseEntity<Map<String, String>> evictCacheKey(
            @PathVariable String cacheName, 
            @PathVariable String key) {
        
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }
        
        cache.evict(key);
        log.info("Evicted key '{}' from cache '{}'", key, cacheName);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cache key evicted successfully");
        response.put("cacheName", cacheName);
        response.put("key", key);
        response.put("timestamp", new Date().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get Redis statistics
     */
    @GetMapping("/redis/stats")
    public ResponseEntity<Map<String, Object>> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get Redis info
            Properties info = redisTemplate.execute(connection -> {
                return connection.serverCommands().info();
            }, true);
            
            if (info != null) {
                // Extract key statistics
                stats.put("redis_version", info.getProperty("redis_version"));
                stats.put("used_memory", info.getProperty("used_memory_human"));
                stats.put("used_memory_peak", info.getProperty("used_memory_peak_human"));
                stats.put("connected_clients", info.getProperty("connected_clients"));
                stats.put("total_connections_received", info.getProperty("total_connections_received"));
                stats.put("total_commands_processed", info.getProperty("total_commands_processed"));
                stats.put("uptime_in_seconds", info.getProperty("uptime_in_seconds"));
                stats.put("uptime_in_days", info.getProperty("uptime_in_days"));
                
                // Get keyspace info
                String keyspace = info.getProperty("db0");
                if (keyspace != null) {
                    stats.put("keyspace", keyspace);
                }
            }
            
            // Count keys by pattern (cache keys)
            Set<String> allKeys = redisTemplate.keys("*");
            if (allKeys != null) {
                stats.put("total_keys", allKeys.size());
                
                // Group keys by cache prefix
                Map<String, Long> keysByPrefix = allKeys.stream()
                        .collect(Collectors.groupingBy(
                                key -> key.contains("::") ? key.substring(0, key.indexOf("::")) : "other",
                                Collectors.counting()
                        ));
                stats.put("keys_by_cache", keysByPrefix);
            }
            
        } catch (Exception e) {
            log.error("Error getting Redis stats: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get all keys from a specific cache
     */
    @GetMapping("/keys/{cacheName}")
    public ResponseEntity<Map<String, Object>> getCacheKeys(@PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Search for keys with the cache prefix
            String pattern = cacheName + "::*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            response.put("cacheName", cacheName);
            response.put("totalKeys", keys != null ? keys.size() : 0);
            response.put("keys", keys != null ? new ArrayList<>(keys).subList(0, Math.min(100, keys.size())) : new ArrayList<>());
            
            if (keys != null && keys.size() > 100) {
                response.put("note", "Showing first 100 keys only");
            }
            
        } catch (Exception e) {
            log.error("Error getting cache keys: {}", e.getMessage());
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get value for a specific cache key
     */
    @GetMapping("/value/{cacheName}/{key}")
    public ResponseEntity<Map<String, Object>> getCacheValue(
            @PathVariable String cacheName,
            @PathVariable String key) {
        
        Map<String, Object> response = new HashMap<>();
        
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }
        
        org.springframework.cache.Cache.ValueWrapper valueWrapper = cache.get(key);
        
        response.put("cacheName", cacheName);
        response.put("key", key);
        
        if (valueWrapper != null) {
            Object value = valueWrapper.get();
            response.put("found", true);
            response.put("value", value);
            response.put("valueType", value != null ? value.getClass().getSimpleName() : "null");
        } else {
            response.put("found", false);
            response.put("message", "Key not found in cache");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Warm up cache by preloading frequently accessed data
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmupCache() {
        Map<String, Object> response = new HashMap<>();
        
        log.info("Starting cache warmup...");
        
        // This would typically call your services to preload data
        // For example:
        // - Load recent queries
        // - Load popular documents
        // - Load user profiles
        
        response.put("message", "Cache warmup initiated");
        response.put("status", "IN_PROGRESS");
        response.put("timestamp", new Date().toString());
        response.put("note", "Implement service calls to preload frequently accessed data");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test cache operations
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testCache() {
        Map<String, Object> response = new HashMap<>();
        List<String> testResults = new ArrayList<>();
        
        String testKey = "test:key:" + UUID.randomUUID();
        String testValue = "Test value at " + new Date();
        String cacheName = "queries"; // Using queries cache for testing
        
        try {
            // Test 1: Put value in cache
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(testKey, testValue);
                testResults.add("✓ Successfully put value in cache");
                
                // Test 2: Get value from cache
                org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(testKey);
                if (wrapper != null && testValue.equals(wrapper.get())) {
                    testResults.add("✓ Successfully retrieved value from cache");
                } else {
                    testResults.add("✗ Failed to retrieve correct value from cache");
                }
                
                // Test 3: Evict value from cache
                cache.evict(testKey);
                wrapper = cache.get(testKey);
                if (wrapper == null) {
                    testResults.add("✓ Successfully evicted value from cache");
                } else {
                    testResults.add("✗ Failed to evict value from cache");
                }
                
                // Test 4: Test Redis connection
                String pingResult = redisTemplate.execute(connection -> {
                    return connection.ping();       
                }, true);
                if ("PONG".equals(pingResult)) {
                    testResults.add("✓ Redis connection is healthy");
                } else {
                    testResults.add("✗ Redis connection issue: " + pingResult);
                }
                
            } else {
                testResults.add("✗ Cache not found: " + cacheName);
            }
            
        } catch (Exception e) {
            testResults.add("✗ Error during cache test: " + e.getMessage());
            log.error("Cache test error", e);
        }
        
        response.put("testResults", testResults);
        response.put("allTestsPassed", testResults.stream().allMatch(r -> r.startsWith("✓")));
        response.put("timestamp", new Date().toString());
        
        return ResponseEntity.ok(response);
    }
}
