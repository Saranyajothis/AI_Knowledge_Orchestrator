package com.aiko.orchestrator.exception;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String message;
    private int status;
    private long timestamp;
    private Map<String, String> validationErrors;
}