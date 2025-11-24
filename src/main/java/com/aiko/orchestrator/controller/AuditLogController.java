package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.model.AuditLog;
import com.aiko.orchestrator.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for viewing and analyzing audit logs
 * Provides endpoints to query request/response logs and analyze system activity
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    
    private final AuditLogRepository auditLogRepository;
    
    /**
     * Get all audit logs with pagination
     */
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<AuditLog> logs = auditLogRepository.findAll(pageable);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get audit logs for a specific user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuditLog>> getUserAuditLogs(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> logs = auditLogRepository.findByUserId(userId, pageable);
        
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get audit logs by request ID
     */
    @GetMapping("/request/{requestId}")
    public ResponseEntity<List<AuditLog>> getLogsByRequestId(@PathVariable String requestId) {
        List<AuditLog> logs = auditLogRepository.findByRequestId(requestId);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get failed requests (4xx and 5xx responses)
     */
    @GetMapping("/failed")
    public ResponseEntity<Page<AuditLog>> getFailedRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> logs = auditLogRepository.findByResponseStatusGreaterThanEqual(400, pageable);
        
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get slow requests
     */
    @GetMapping("/slow")
    public ResponseEntity<List<AuditLog>> getSlowRequests(
            @RequestParam(defaultValue = "5000") Long thresholdMs) {
        
        List<AuditLog> logs = auditLogRepository.findSlowRequests(thresholdMs);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get requests with errors
     */
    @GetMapping("/errors")
    public ResponseEntity<List<AuditLog>> getErrorLogs() {
        List<AuditLog> logs = auditLogRepository.findByErrorMessageIsNotNull();
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get audit logs by time range
     */
    @GetMapping("/range")
    public ResponseEntity<List<AuditLog>> getLogsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        List<AuditLog> logs = auditLogRepository.findByTimestampBetween(start, end);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get requests from a specific IP address
     */
    @GetMapping("/ip/{clientIp}")
    public ResponseEntity<List<AuditLog>> getLogsByClientIp(@PathVariable String clientIp) {
        List<AuditLog> logs = auditLogRepository.findByClientIp(clientIp);
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get request statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getRequestStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        // Default to last 24 hours if not specified
        if (start == null) {
            start = LocalDateTime.now().minusHours(24);
        }
        if (end == null) {
            end = LocalDateTime.now();
        }
        
        List<AuditLog> logs = auditLogRepository.findByTimestampBetween(start, end);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", logs.size());
        
        // Calculate success/failure rates
        long successCount = logs.stream()
                .filter(log -> log.getResponseStatus() != null && log.getResponseStatus() < 400)
                .count();
        long failureCount = logs.stream()
                .filter(log -> log.getResponseStatus() != null && log.getResponseStatus() >= 400)
                .count();
        
        stats.put("successCount", successCount);
        stats.put("failureCount", failureCount);
        stats.put("successRate", logs.isEmpty() ? 0 : (double) successCount / logs.size() * 100);
        
        // Calculate average response time
        double avgResponseTime = logs.stream()
                .filter(log -> log.getProcessingTimeMs() != null)
                .mapToLong(AuditLog::getProcessingTimeMs)
                .average()
                .orElse(0);
        
        stats.put("averageResponseTime", avgResponseTime);
        
        // Group by HTTP method
        Map<String, Long> methodCounts = logs.stream()
                .filter(log -> log.getHttpMethod() != null)
                .collect(Collectors.groupingBy(
                        AuditLog::getHttpMethod,
                        Collectors.counting()
                ));
        stats.put("methodDistribution", methodCounts);
        
        // Group by response status
        Map<String, Long> statusCounts = logs.stream()
                .filter(log -> log.getResponseStatus() != null)
                .collect(Collectors.groupingBy(
                        log -> getStatusCategory(log.getResponseStatus()),
                        Collectors.counting()
                ));
        stats.put("statusDistribution", statusCounts);
        
        // Find slowest endpoints
        List<Map<String, Object>> slowestEndpoints = logs.stream()
                .filter(log -> log.getProcessingTimeMs() != null && log.getRequestUri() != null)
                .sorted((a, b) -> b.getProcessingTimeMs().compareTo(a.getProcessingTimeMs()))
                .limit(10)
                .map(log -> {
                    Map<String, Object> endpoint = new HashMap<>();
                    endpoint.put("method", log.getHttpMethod());
                    endpoint.put("uri", log.getRequestUri());
                    endpoint.put("processingTime", log.getProcessingTimeMs());
                    endpoint.put("timestamp", log.getTimestamp());
                    return endpoint;
                })
                .collect(Collectors.toList());
        stats.put("slowestEndpoints", slowestEndpoints);
        
        // Most frequent endpoints
        Map<String, Long> endpointCounts = logs.stream()
                .filter(log -> log.getRequestUri() != null)
                .collect(Collectors.groupingBy(
                        log -> log.getHttpMethod() + " " + log.getRequestUri(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
        stats.put("mostFrequentEndpoints", endpointCounts);
        
        // Error summary
        long errorCount = logs.stream()
                .filter(log -> log.getErrorMessage() != null)
                .count();
        stats.put("errorCount", errorCount);
        
        // Time range
        stats.put("timeRange", Map.of("start", start, "end", end));
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get request statistics summary for dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        LocalDateTime now = LocalDateTime.now();
        
        Map<String, Object> dashboard = new HashMap<>();
        
        // Last hour stats
        LocalDateTime oneHourAgo = now.minusHours(1);
        List<AuditLog> lastHourLogs = auditLogRepository.findByTimestampBetween(oneHourAgo, now);
        dashboard.put("lastHourRequests", lastHourLogs.size());
        
        // Last 24 hours stats
        LocalDateTime oneDayAgo = now.minusDays(1);
        List<AuditLog> lastDayLogs = auditLogRepository.findByTimestampBetween(oneDayAgo, now);
        dashboard.put("last24HourRequests", lastDayLogs.size());
        
        // Calculate error rate for last hour
        long lastHourErrors = lastHourLogs.stream()
                .filter(log -> log.getResponseStatus() != null && log.getResponseStatus() >= 500)
                .count();
        dashboard.put("lastHourErrorRate", 
                     lastHourLogs.isEmpty() ? 0 : (double) lastHourErrors / lastHourLogs.size() * 100);
        
        // Current active requests (processing)
        long activeRequests = lastHourLogs.stream()
                .filter(log -> log.getResponseStatus() == null)
                .count();
        dashboard.put("activeRequests", activeRequests);
        
        // Average response time for last hour
        double avgResponseTime = lastHourLogs.stream()
                .filter(log -> log.getProcessingTimeMs() != null)
                .mapToLong(AuditLog::getProcessingTimeMs)
                .average()
                .orElse(0);
        dashboard.put("averageResponseTime", Math.round(avgResponseTime));
        
        // Slow request count (> 5 seconds)
        long slowRequestCount = lastHourLogs.stream()
                .filter(log -> log.getProcessingTimeMs() != null && log.getProcessingTimeMs() > 5000)
                .count();
        dashboard.put("slowRequestCount", slowRequestCount);
        
        return ResponseEntity.ok(dashboard);
    }
    
    /**
     * Delete old audit logs
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        
        List<AuditLog> oldLogs = auditLogRepository.findByTimestampBetween(
                LocalDateTime.of(2020, 1, 1, 0, 0), before);
        
        int count = oldLogs.size();
        auditLogRepository.deleteAll(oldLogs);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Deleted old audit logs");
        result.put("count", count);
        result.put("before", before);
        
        log.info("Cleaned up {} old audit logs before {}", count, before);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Helper method to categorize status codes
     */
    private String getStatusCategory(int status) {
        if (status < 200) return "1xx";
        if (status < 300) return "2xx";
        if (status < 400) return "3xx";
        if (status < 500) return "4xx";
        return "5xx";
    }
}
