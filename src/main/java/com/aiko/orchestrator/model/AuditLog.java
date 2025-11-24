package com.aiko.orchestrator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String entityId; // ID of the affected entity
    
    @Indexed
    private String entityType; // Query, Response, Knowledge, etc.
    
    @Indexed
    private ActionType action;
    
    private String description;
    
    private Map<String, Object> changes; // What was changed
    
    private String ipAddress;
    
    private String userAgent;
    
    // Request/Response logging fields
    @Indexed
    private String requestId;
    private String httpMethod;
    private String requestUri;
    private String queryString;
    private String clientIp;
    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
    private Long processingTimeMs;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private String errorMessage;
    private String errorStackTrace;
    
    @CreatedDate
    @Indexed
    private LocalDateTime timestamp;
    
    public enum ActionType {
        CREATE, READ, UPDATE, DELETE, VIEW,
        LOGIN, LOGOUT, 
        QUERY_SUBMITTED, QUERY_PROCESSED, QUERY_CANCELLED,
        DOCUMENT_UPLOADED, DOCUMENT_DELETED, DOCUMENT_INDEXED,
        SUCCESS, FAILED, ERROR, WARNING, OTHER
    }
}