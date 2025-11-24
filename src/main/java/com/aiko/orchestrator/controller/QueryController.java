package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.annotation.RateLimit;
import com.aiko.orchestrator.dto.QueryRequest;
import com.aiko.orchestrator.dto.QueryResponse;
import com.aiko.orchestrator.service.QueryService;
import com.aiko.orchestrator.annotation.RateLimit;
import com.aiko.orchestrator.ratelimit.RateLimitConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/queries")
@RequiredArgsConstructor
@Tag(name = "Query Management", description = "Endpoints for managing AI queries")
public class QueryController {
    
    private final QueryService queryService;
    
    @PostMapping("/submit")
    @Operation(summary = "Submit a new query for AI processing")
    @RateLimit(capacity = 50, refillPeriodSeconds = 60, keyType = RateLimit.RateLimitKeyType.IP, errorMessage = "Too many queries. Please wait before submitting more.")

    public ResponseEntity<QueryResponse> submitQuery(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.submitQuery(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get query status and response by ID")
    public ResponseEntity<QueryResponse> getQuery(@PathVariable String id) {
        QueryResponse response = queryService.getQueryById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/history")
    @Operation(summary = "Get query history with pagination")
    public ResponseEntity<Page<QueryResponse>> getQueryHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String userId) {
        
        Page<QueryResponse> history = queryService.getQueryHistory(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(history);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a pending query")
    public ResponseEntity<Void> cancelQuery(@PathVariable String id) {
        queryService.cancelQuery(id);
        return ResponseEntity.noContent().build();
    }
}