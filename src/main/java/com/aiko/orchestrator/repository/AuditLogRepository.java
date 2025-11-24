package com.aiko.orchestrator.repository;

import com.aiko.orchestrator.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    
    Page<AuditLog> findByUserId(String userId, Pageable pageable);
    
    List<AuditLog> findByEntityIdAndEntityType(String entityId, String entityType);
    
    List<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<AuditLog> findByActionIn(List<AuditLog.ActionType> actions);
    
    // Additional queries for request/response logging
    List<AuditLog> findByRequestId(String requestId);
    
    Page<AuditLog> findByResponseStatusGreaterThanEqual(Integer status, Pageable pageable);
    
    Page<AuditLog> findByProcessingTimeMsGreaterThan(Long milliseconds, Pageable pageable);
    
    List<AuditLog> findByClientIp(String clientIp);
    
    Page<AuditLog> findByHttpMethodAndRequestUriContaining(String method, String uriPart, Pageable pageable);
    
    List<AuditLog> findByErrorMessageIsNotNull();
    
    @org.springframework.data.mongodb.repository.Query("{ 'responseStatus': { $gte: 400 }, 'timestamp': { $gte: ?0, $lte: ?1 } }")
    List<AuditLog> findFailedRequestsInTimeRange(LocalDateTime start, LocalDateTime end);
    
    @org.springframework.data.mongodb.repository.Query("{ 'processingTimeMs': { $gte: ?0 } }")
    List<AuditLog> findSlowRequests(Long thresholdMs);
    
    // Aggregation for request statistics
    @org.springframework.data.mongodb.repository.Aggregation(
        "{ $match: { timestamp: { $gte: ?0, $lte: ?1 } } }," +
        "{ $group: { _id: '$httpMethod', count: { $sum: 1 }, avgTime: { $avg: '$processingTimeMs' } } }"
    )
    List<Object> getRequestStatsByMethod(LocalDateTime start, LocalDateTime end);
}