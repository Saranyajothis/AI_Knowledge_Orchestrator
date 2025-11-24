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
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "responses")
public class Response implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private String id;
    
    @Indexed
    private String queryId; // Reference to Query document
    
    private String responseText;
    
    private String agentType; // Which agent generated this
    
    private Double confidenceScore;
    
    private List<String> sources; // URLs or document IDs used
    
    private Map<String, Object> metadata; // Additional AI response metadata
    
    private String modelUsed; // e.g., "gpt-4", "claude-3"
    
    private Integer tokensUsed;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    private ResponseStatus status = ResponseStatus.SUCCESS;
    
    public enum ResponseStatus {
        SUCCESS, PARTIAL, ERROR, TIMEOUT
    }
}