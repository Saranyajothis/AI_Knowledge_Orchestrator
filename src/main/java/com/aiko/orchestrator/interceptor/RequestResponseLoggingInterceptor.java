package com.aiko.orchestrator.interceptor;

import com.aiko.orchestrator.model.AuditLog;
import com.aiko.orchestrator.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interceptor that logs all HTTP requests and responses.
 * Captures request details, response status, processing time, and stores in AuditLog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestResponseLoggingInterceptor implements HandlerInterceptor {

    private final AuditLogRepository auditLogRepository;
    
    // ThreadLocal to store request start time and details
    private static final ThreadLocal<RequestInfo> REQUEST_INFO = new ThreadLocal<>();
    
    /**
     * Pre-handle: Called before the handler method
     * Logs incoming request details
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // Store request start time
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.startTime = LocalDateTime.now();
        requestInfo.requestId = UUID.randomUUID().toString();
        REQUEST_INFO.set(requestInfo);
        
        // Add request ID to response header for tracking
        response.addHeader("X-Request-Id", requestInfo.requestId);
        
        // Log request details
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        log.info("==> REQUEST START [{}] {} {} from {} | Request-ID: {}", 
                method, fullUrl, request.getProtocol(), clientIp, requestInfo.requestId);
        
        // Store request details for later use
        requestInfo.method = method;
        requestInfo.uri = uri;
        requestInfo.queryString = queryString;
        requestInfo.clientIp = clientIp;
        requestInfo.userAgent = userAgent;
        requestInfo.headers = getHeadersAsMap(request);
        
        // Log headers if debug enabled
        if (log.isDebugEnabled()) {
            log.debug("Request Headers: {}", requestInfo.headers);
        }
        
        // Create initial audit log entry
        AuditLog auditLog = new AuditLog();
        auditLog.setRequestId(requestInfo.requestId);
        auditLog.setHttpMethod(method);
        auditLog.setRequestUri(uri);
        auditLog.setQueryString(queryString);
        auditLog.setClientIp(clientIp);
        auditLog.setUserAgent(userAgent);
        auditLog.setRequestHeaders(requestInfo.headers);
        auditLog.setRequestTime(requestInfo.startTime);
        auditLog.setAction(determineAction(method, uri));
        auditLog.setEntityType("HTTP_REQUEST");
        
        // Extract user ID from header or auth context if available
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            userId = "anonymous";
        }
        auditLog.setUserId(userId);
        
        // Save initial audit log
        auditLogRepository.save(auditLog);
        requestInfo.auditLogId = auditLog.getId();
        
        return true; // Continue with request processing
    }
    
    /**
     * Post-handle: Called after handler method but before view rendering
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) throws Exception {
        
        RequestInfo requestInfo = REQUEST_INFO.get();
        if (requestInfo != null) {
            requestInfo.handlerCompleteTime = LocalDateTime.now();
            
            if (log.isDebugEnabled()) {
                log.debug("Handler completed for Request-ID: {} in {}ms", 
                        requestInfo.requestId,
                        ChronoUnit.MILLIS.between(requestInfo.startTime, requestInfo.handlerCompleteTime));
            }
        }
    }
    
    /**
     * After completion: Called after complete request processing
     * Logs response details and processing time
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) throws Exception {
        
        RequestInfo requestInfo = REQUEST_INFO.get();
        if (requestInfo == null) {
            return;
        }
        
        try {
            LocalDateTime endTime = LocalDateTime.now();
            long processingTime = ChronoUnit.MILLIS.between(requestInfo.startTime, endTime);
            
            // Log response details
            int statusCode = response.getStatus();
            String statusText = getStatusText(statusCode);
            
            // Determine log level based on status code
            if (statusCode >= 500) {
                log.error("<== RESPONSE [{}] {} {} | {} | {}ms | Request-ID: {}", 
                        requestInfo.method, requestInfo.uri, statusCode, statusText, 
                        processingTime, requestInfo.requestId);
            } else if (statusCode >= 400) {
                log.warn("<== RESPONSE [{}] {} {} | {} | {}ms | Request-ID: {}", 
                        requestInfo.method, requestInfo.uri, statusCode, statusText, 
                        processingTime, requestInfo.requestId);
            } else {
                log.info("<== RESPONSE [{}] {} {} | {} | {}ms | Request-ID: {}", 
                        requestInfo.method, requestInfo.uri, statusCode, statusText, 
                        processingTime, requestInfo.requestId);
            }
            
            // Update audit log with response details
            if (requestInfo.auditLogId != null) {
                auditLogRepository.findById(requestInfo.auditLogId).ifPresent(auditLog -> {
                    auditLog.setResponseStatus(statusCode);
                    auditLog.setResponseTime(endTime);
                    auditLog.setProcessingTimeMs(processingTime);
                    auditLog.setResponseHeaders(getResponseHeadersAsMap(response));
                    
                    if (ex != null) {
                        auditLog.setErrorMessage(ex.getMessage());
                        auditLog.setErrorStackTrace(getStackTraceAsString(ex));
                        auditLog.setAction(AuditLog.ActionType.ERROR);
                    } else {
                        auditLog.setAction(statusCode < 400 ? 
                                         AuditLog.ActionType.SUCCESS : 
                                         AuditLog.ActionType.FAILED);
                    }
                    
                    auditLog.setDescription(String.format("%s %s - Status: %d - Time: %dms",
                            requestInfo.method, requestInfo.uri, statusCode, processingTime));
                    
                    auditLogRepository.save(auditLog);
                });
            }
            
            // Log exception if present
            if (ex != null) {
                log.error("Request processing failed for Request-ID: {} - Error: {}", 
                         requestInfo.requestId, ex.getMessage(), ex);
            }
            
            // Log slow requests
            if (processingTime > 5000) { // More than 5 seconds
                log.warn("SLOW REQUEST: [{}] {} took {}ms | Request-ID: {}", 
                        requestInfo.method, requestInfo.uri, processingTime, requestInfo.requestId);
            }
            
        } finally {
            // Clean up ThreadLocal
            REQUEST_INFO.remove();
        }
    }
    
    /**
     * Get client IP address, checking for proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Real-IP"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (when passing through multiple proxies)
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Convert request headers to Map for storage
     */
    private Map<String, String> getHeadersAsMap(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // Skip sensitive headers
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        
        return headers;
    }
    
    /**
     * Convert response headers to Map for storage
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
     * Check if header contains sensitive information
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
     * Determine audit action type based on HTTP method and URI
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
     * Get descriptive status text for HTTP status code
     */
    private String getStatusText(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Status " + statusCode;
        };
    }
    
    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception ex) {
        if (ex == null) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
        
        StackTraceElement[] stackTrace = ex.getStackTrace();
        int limit = Math.min(stackTrace.length, 10); // Limit stack trace depth
        
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
        }
        
        if (stackTrace.length > limit) {
            sb.append("\t... ").append(stackTrace.length - limit).append(" more\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Inner class to store request information in ThreadLocal
     */
    private static class RequestInfo {
        String requestId;
        LocalDateTime startTime;
        LocalDateTime handlerCompleteTime;
        String method;
        String uri;
        String queryString;
        String clientIp;
        String userAgent;
        Map<String, String> headers;
        String auditLogId;
    }
}
