package com.aiko.orchestrator.filter;

import com.aiko.orchestrator.model.AuditLog;
import com.aiko.orchestrator.repository.AuditLogRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Alternative to Interceptor - Filter approach for logging requests and responses.
 * This approach allows access to request and response bodies.
 * Use either this OR the interceptor, not both.
 */
@Slf4j
@Component
@Order(1) // Execute this filter first
@RequiredArgsConstructor
public class LoggingFilter implements Filter {
    
    private final AuditLogRepository auditLogRepository;
    
    // Request attributes to pass data between filter invocations
    private static final String START_TIME = "startTime";
    private static final String REQUEST_ID = "requestId";
    private static final String AUDIT_LOG_ID = "auditLogId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Skip logging for certain paths
        if (shouldSkipLogging(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        
        // Wrap request and response to cache content for logging
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
        
        // Generate request ID and store start time
        String requestId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();
        
        requestWrapper.setAttribute(REQUEST_ID, requestId);
        requestWrapper.setAttribute(START_TIME, startTime);
        
        // Add request ID to response header
        responseWrapper.addHeader("X-Request-Id", requestId);
        
        try {
            // Log request
            logRequest(requestWrapper, requestId, startTime);
            
            // Process the request
            chain.doFilter(requestWrapper, responseWrapper);
            
            // Log response
            logResponse(requestWrapper, responseWrapper);
            
        } catch (Exception e) {
            // Log error
            logError(requestWrapper, responseWrapper, e);
            throw e;
        } finally {
            // Copy response body back to original response
            responseWrapper.copyBodyToResponse();
        }
    }
    
    /**
     * Log incoming request details
     */
    private void logRequest(ContentCachingRequestWrapper request, String requestId, LocalDateTime startTime) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = getClientIp(request);
        
        log.info("==> REQUEST [{}] {} {} from {} | Request-ID: {}", 
                method, fullUrl, request.getProtocol(), clientIp, requestId);
        
        // Create audit log entry
        AuditLog auditLog = new AuditLog();
        auditLog.setRequestId(requestId);
        auditLog.setHttpMethod(method);
        auditLog.setRequestUri(uri);
        auditLog.setQueryString(queryString);
        auditLog.setClientIp(clientIp);
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setRequestHeaders(getHeadersAsMap(request));
        auditLog.setRequestTime(startTime);
        auditLog.setAction(determineAction(method, uri));
        auditLog.setEntityType("HTTP_REQUEST");
        
        // Extract user ID if available
        String userId = request.getHeader("X-User-Id");
        auditLog.setUserId(userId != null ? userId : "anonymous");
        
        // Get request body if present
        String requestBody = getRequestBody(request);
        if (requestBody != null && !requestBody.isEmpty()) {
            // Truncate large bodies
            if (requestBody.length() > 5000) {
                requestBody = requestBody.substring(0, 5000) + "... (truncated)";
            }
            auditLog.setRequestBody(requestBody);
            
            if (log.isDebugEnabled()) {
                log.debug("Request Body: {}", requestBody);
            }
        }
        
        // Save audit log
        AuditLog savedLog = auditLogRepository.save(auditLog);
        request.setAttribute(AUDIT_LOG_ID, savedLog.getId());
    }
    
    /**
     * Log response details
     */
    private void logResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        LocalDateTime startTime = (LocalDateTime) request.getAttribute(START_TIME);
        String requestId = (String) request.getAttribute(REQUEST_ID);
        String auditLogId = (String) request.getAttribute(AUDIT_LOG_ID);
        
        if (startTime == null || requestId == null) {
            return;
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        long processingTime = ChronoUnit.MILLIS.between(startTime, endTime);
        
        int statusCode = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        
        // Log based on status code
        if (statusCode >= 500) {
            log.error("<== RESPONSE [{}] {} {} | {}ms | Request-ID: {}", 
                    method, uri, statusCode, processingTime, requestId);
        } else if (statusCode >= 400) {
            log.warn("<== RESPONSE [{}] {} {} | {}ms | Request-ID: {}", 
                    method, uri, statusCode, processingTime, requestId);
        } else {
            log.info("<== RESPONSE [{}] {} {} | {}ms | Request-ID: {}", 
                    method, uri, statusCode, processingTime, requestId);
        }
        
        // Update audit log with response details
        if (auditLogId != null) {
            auditLogRepository.findById(auditLogId).ifPresent(auditLog -> {
                auditLog.setResponseStatus(statusCode);
                auditLog.setResponseTime(endTime);
                auditLog.setProcessingTimeMs(processingTime);
                auditLog.setResponseHeaders(getResponseHeadersAsMap(response));
                
                // Get response body
                String responseBody = getResponseBody(response);
                if (responseBody != null && !responseBody.isEmpty()) {
                    // Truncate large bodies
                    if (responseBody.length() > 5000) {
                        responseBody = responseBody.substring(0, 5000) + "... (truncated)";
                    }
                    auditLog.setResponseBody(responseBody);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("Response Body: {}", responseBody);
                    }
                }
                
                auditLog.setAction(statusCode < 400 ? 
                                 AuditLog.ActionType.SUCCESS : 
                                 AuditLog.ActionType.FAILED);
                
                auditLog.setDescription(String.format("%s %s - Status: %d - Time: %dms",
                        method, uri, statusCode, processingTime));
                
                auditLogRepository.save(auditLog);
            });
        }
        
        // Log slow requests
        if (processingTime > 5000) {
            log.warn("SLOW REQUEST: [{}] {} took {}ms | Request-ID: {}", 
                    method, uri, processingTime, requestId);
        }
    }
    
    /**
     * Log error details
     */
    private void logError(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, Exception ex) {
        String requestId = (String) request.getAttribute(REQUEST_ID);
        String auditLogId = (String) request.getAttribute(AUDIT_LOG_ID);
        
        log.error("Request processing failed for Request-ID: {} - Error: {}", 
                 requestId, ex.getMessage(), ex);
        
        // Update audit log with error details
        if (auditLogId != null) {
            auditLogRepository.findById(auditLogId).ifPresent(auditLog -> {
                auditLog.setErrorMessage(ex.getMessage());
                auditLog.setErrorStackTrace(getStackTraceAsString(ex));
                auditLog.setAction(AuditLog.ActionType.ERROR);
                auditLog.setResponseStatus(500);
                auditLogRepository.save(auditLog);
            });
        }
    }
    
    /**
     * Check if logging should be skipped for this URI
     */
    private boolean shouldSkipLogging(String uri) {
        return uri.contains("/health") || 
               uri.contains("/actuator") || 
               uri.contains("/swagger") || 
               uri.contains("/docs") ||
               uri.contains("/favicon.ico") ||
               uri.contains("/static/") ||
               uri.contains("/css/") ||
               uri.contains("/js/");
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
    
    /**
     * Get request headers as map
     */
    private Map<String, String> getHeadersAsMap(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        
        return headers;
    }
    
    /**
     * Get response headers as map
     */
    private Map<String, String> getResponseHeadersAsMap(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        Collection<String> headerNames = response.getHeaderNames();
        
        for (String headerName : headerNames) {
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, response.getHeader(headerName));
            }
        }
        
        return headers;
    }
    
    /**
     * Check if header is sensitive
     */
    private boolean isSensitiveHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.contains("authorization") || 
               lower.contains("cookie") || 
               lower.contains("token") ||
               lower.contains("api-key") ||
               lower.contains("secret");
    }
    
    /**
     * Get request body from cached wrapper
     */
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }
    
    /**
     * Get response body from cached wrapper
     */
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }
    
    /**
     * Determine action type from HTTP method
     */
    private AuditLog.ActionType determineAction(String method, String uri) {
        return switch (method.toUpperCase()) {
            case "GET" -> AuditLog.ActionType.READ;
            case "POST" -> uri.contains("query") ? AuditLog.ActionType.QUERY_SUBMITTED : AuditLog.ActionType.CREATE;
            case "PUT", "PATCH" -> AuditLog.ActionType.UPDATE;
            case "DELETE" -> AuditLog.ActionType.DELETE;
            default -> AuditLog.ActionType.OTHER;
        };
    }
    
    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
        
        StackTraceElement[] stackTrace = ex.getStackTrace();
        int limit = Math.min(stackTrace.length, 10);
        
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
        }
        
        if (stackTrace.length > limit) {
            sb.append("\t... ").append(stackTrace.length - limit).append(" more\n");
        }
        
        return sb.toString();
    }
}
