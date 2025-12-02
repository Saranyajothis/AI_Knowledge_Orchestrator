package com.aiko.orchestrator.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    
    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-Id";
    private static final String CORRELATION_ID_LOG_VAR_NAME = "correlationId";
    private static final String REQUEST_ID_LOG_VAR_NAME = "requestId";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        final String correlationId = extractOrGenerateCorrelationId(request);
        final String requestId = UUID.randomUUID().toString();
        
        // Add to MDC for logging
        MDC.put(CORRELATION_ID_LOG_VAR_NAME, correlationId);
        MDC.put(REQUEST_ID_LOG_VAR_NAME, requestId);
        MDC.put("requestPath", request.getRequestURI());
        MDC.put("requestMethod", request.getMethod());
        
        // Add to response header
        response.addHeader(CORRELATION_ID_HEADER_NAME, correlationId);
        response.addHeader("X-Request-Id", requestId);
        
        log.info("Processing request: {} {} with correlationId: {}", 
                request.getMethod(), 
                request.getRequestURI(), 
                correlationId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("Completed request: {} {} with correlationId: {}, status: {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    correlationId,
                    response.getStatus());
            
            // Clean up MDC
            MDC.remove(CORRELATION_ID_LOG_VAR_NAME);
            MDC.remove(REQUEST_ID_LOG_VAR_NAME);
            MDC.remove("requestPath");
            MDC.remove("requestMethod");
        }
    }
    
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);
        
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Found existing correlation ID: {}", correlationId);
        }
        
        return correlationId;
    }
}
