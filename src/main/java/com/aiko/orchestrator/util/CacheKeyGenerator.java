package com.aiko.orchestrator.util;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom cache key generator for complex scenarios
 * Provides different strategies for generating cache keys
 */
@Component
public class CacheKeyGenerator {
    
    /**
     * Generate cache key for pageable queries
     */
    public static String generateKeyForPageable(String prefix, Pageable pageable, Object... additionalParams) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        keyBuilder.append(":page:").append(pageable.getPageNumber());
        keyBuilder.append(":size:").append(pageable.getPageSize());
        
        if (pageable.getSort().isSorted()) {
            keyBuilder.append(":sort:");
            pageable.getSort().forEach(order -> 
                keyBuilder.append(order.getProperty()).append("-").append(order.getDirection())
            );
        }
        
        // Add additional parameters
        for (Object param : additionalParams) {
            if (param != null) {
                keyBuilder.append(":").append(param.toString());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Generate cache key for search queries
     */
    public static String generateKeyForSearch(String searchTerm, int limit, String... filters) {
        StringBuilder keyBuilder = new StringBuilder("search:");
        keyBuilder.append(normalizeSearchTerm(searchTerm));
        keyBuilder.append(":limit:").append(limit);
        
        if (filters != null && filters.length > 0) {
            keyBuilder.append(":filters:");
            keyBuilder.append(String.join("-", filters));
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Generate cache key for user-specific data
     */
    public static String generateKeyForUser(String userId, String dataType, Object... params) {
        StringBuilder keyBuilder = new StringBuilder("user:");
        keyBuilder.append(userId);
        keyBuilder.append(":").append(dataType);
        
        for (Object param : params) {
            if (param != null) {
                keyBuilder.append(":").append(param.toString());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Generate cache key for entity by ID
     */
    public static String generateKeyForEntity(String entityType, String entityId) {
        return String.format("%s:id:%s", entityType, entityId);
    }
    
    /**
     * Generate cache key for list operations
     */
    public static String generateKeyForList(String entityType, List<String> ids) {
        String idList = ids.stream()
                .sorted()
                .collect(Collectors.joining(","));
        return String.format("%s:ids:%s", entityType, idList);
    }
    
    /**
     * Normalize search terms for consistent cache keys
     */
    private static String normalizeSearchTerm(String searchTerm) {
        if (!StringUtils.hasText(searchTerm)) {
            return "empty";
        }
        
        return searchTerm.toLowerCase()
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
    
    /**
     * Custom KeyGenerator implementation for Spring Cache
     */
    @Component("customKeyGenerator")
    public static class CustomKeyGenerator implements KeyGenerator {
        
        @Override
        public Object generate(Object target, Method method, Object... params) {
            StringBuilder sb = new StringBuilder();
            
            // Add class name
            sb.append(target.getClass().getSimpleName()).append(".");
            
            // Add method name
            sb.append(method.getName());
            
            // Add parameters
            if (params.length > 0) {
                sb.append(":");
                sb.append(Arrays.stream(params)
                        .map(param -> param != null ? param.toString() : "null")
                        .collect(Collectors.joining(",")));
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Generate cache key for AI responses
     */
    public static String generateKeyForAIResponse(String model, String prompt, String userId) {
        String normalizedPrompt = normalizeSearchTerm(prompt);
        if (normalizedPrompt.length() > 100) {
            // Use hash for very long prompts
            normalizedPrompt = String.valueOf(prompt.hashCode());
        }
        
        return String.format("ai:%s:%s:%s", model, normalizedPrompt, userId);
    }
    
    /**
     * Generate cache key for statistics
     */
    public static String generateKeyForStats(String statType, String timeRange, Object... params) {
        StringBuilder keyBuilder = new StringBuilder("stats:");
        keyBuilder.append(statType);
        keyBuilder.append(":").append(timeRange);
        
        for (Object param : params) {
            if (param != null) {
                keyBuilder.append(":").append(param.toString());
            }
        }
        
        return keyBuilder.toString();
    }
}
