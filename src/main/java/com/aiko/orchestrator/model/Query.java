package com.aiko.orchestrator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "queries")
public class Query implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private String queryText;
    
    private QueryType queryType; // RESEARCH, CODE, DECISION
    
    @Indexed
    private QueryStatus status = QueryStatus.PENDING;
    
    private String responseId; // Reference to Response document
    
    private List<String> tags;
    
    private Integer priority = 0;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private LocalDateTime completedAt;
    
    private Long processingTimeMs;
    
    public enum QueryType {
        RESEARCH, CODE, DECISION, GENERAL
    }
    
    public enum QueryStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
}