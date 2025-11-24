package com.aiko.orchestrator.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@Data
public class QueryRequest {
    
    @NotBlank(message = "Query text is required")
    @Size(min = 3, max = 5000, message = "Query must be between 3 and 5000 characters")
    private String queryText;
    
    private String queryType = "GENERAL";
    
    @Min(0)
    @Max(10)
    private Integer priority = 0;
    
    private List<String> tags;
    
    private String userId;
}