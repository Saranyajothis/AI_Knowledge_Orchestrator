package com.aiko.orchestrator.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class QueryResponse {
    private String id;
    private String queryText;
    private String status;
    private String responseText;
    private Double confidenceScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
}