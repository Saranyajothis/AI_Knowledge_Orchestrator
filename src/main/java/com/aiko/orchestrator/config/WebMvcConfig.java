package com.aiko.orchestrator.config;

import com.aiko.orchestrator.interceptor.RequestResponseLoggingInterceptor;
import com.aiko.orchestrator.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * Registers interceptors, CORS settings, and other web-related configurations
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RequestResponseLoggingInterceptor requestResponseLoggingInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    
    /**
     * Register interceptors
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the request/response logging interceptor
        registry.addInterceptor(requestResponseLoggingInterceptor)
                .addPathPatterns("/api/**") // Apply to all API endpoints
                .excludePathPatterns(
                    "/api/v1/health/**",     // Exclude health checks
                    "/api/v1/actuator/**",   // Exclude actuator endpoints
                    "/api/v1/swagger/**",    // Exclude Swagger UI
                    "/api/v1/docs/**"        // Exclude API documentation
                )
                .order(1); // Set order (lower values have higher priority)
        
        // Register the rate limit interceptor
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**") // Apply to all API endpoints
                .excludePathPatterns(
                    "/api/v1/health/**",     // Exclude health checks
                    "/api/v1/actuator/**"    // Exclude actuator endpoints
                )
                .order(2); // Execute after logging interceptor
        
        // You can add more interceptors here with different patterns and orders
    }
}
