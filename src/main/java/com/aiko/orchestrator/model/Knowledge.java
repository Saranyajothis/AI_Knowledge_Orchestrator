package com.aiko.orchestrator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "knowledge")
public class Knowledge implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private String id;
    
    @Indexed
    private String title;
    
    @TextIndexed(weight = 2)
    private String content;
    
    @TextIndexed
    private String summary;
    
    private DocumentType documentType;
    
    @Indexed
    private List<String> tags;
    
    private String source; // URL or file path
    
    private String uploadedBy;
    
    private Map<String, Object> metadata;
    
    private List<Double> embeddings; // For vector search later
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private Long sizeInBytes;
    
    private Integer usageCount = 0;
    
    // Async processing fields
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;
    private LocalDateTime processingStartTime;
    private LocalDateTime processingEndTime;
    private LocalDateTime indexedAt;
    private String errorMessage;
    private List<String> extractedEntities;
    
    public enum DocumentType {
        PDF, TEXT, MARKDOWN, CODE, WEB_PAGE, API_DOC, OTHER
    }
    
    public enum ProcessingStatus {
        PENDING,     // Waiting to be processed
        PROCESSING,  // Currently being processed
        INDEXED,     // Successfully processed and indexed
        FAILED       // Processing failed
    }
}