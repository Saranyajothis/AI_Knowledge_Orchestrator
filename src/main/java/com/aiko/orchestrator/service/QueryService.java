package com.aiko.orchestrator.service;

import com.aiko.orchestrator.dto.QueryRequest;
import com.aiko.orchestrator.dto.QueryResponse;
import com.aiko.orchestrator.exception.ResourceNotFoundException;
import com.aiko.orchestrator.model.Query;
import com.aiko.orchestrator.model.Response;
import com.aiko.orchestrator.model.AuditLog;
import com.aiko.orchestrator.repository.QueryRepository;
import com.aiko.orchestrator.repository.ResponseRepository;
import com.aiko.orchestrator.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {
    
    private final QueryRepository queryRepository;
    private final ResponseRepository responseRepository;
    private final AuditLogRepository auditLogRepository;
    
    public QueryResponse submitQuery(QueryRequest request) {
        // Create and save query
        Query query = new Query();
        query.setUserId(request.getUserId());
        query.setQueryText(request.getQueryText());
        query.setQueryType(Query.QueryType.valueOf(request.getQueryType()));
        query.setPriority(request.getPriority());
        query.setTags(request.getTags());
        query.setStatus(Query.QueryStatus.PENDING);
        
        Query savedQuery = queryRepository.save(query);
        
        // Log the action
        logAction(savedQuery.getUserId(), savedQuery.getId(), "Query", 
                 AuditLog.ActionType.QUERY_SUBMITTED, "Query submitted for processing");
        
        // Process query asynchronously
        processQueryAsync(savedQuery);
        
        return QueryResponse.builder()
                .id(savedQuery.getId())
                .queryText(savedQuery.getQueryText())
                .status(savedQuery.getStatus().toString())
                .createdAt(savedQuery.getCreatedAt())
                .build();
    }
    
    @Async
    public CompletableFuture<Void> processQueryAsync(Query query) {
        try {
            // Update status to processing
            query.setStatus(Query.QueryStatus.PROCESSING);
            queryRepository.save(query);
            
            // Simulate AI processing (replace with actual AI call later)
            Thread.sleep(2000);
            
            // Create mock response
            Response response = new Response();
            response.setQueryId(query.getId());
            response.setResponseText("This is a mock AI response for: " + query.getQueryText());
            response.setAgentType("MOCK_AGENT");
            response.setConfidenceScore(0.85);
            response.setModelUsed("mock-model-1.0");
            response.setStatus(Response.ResponseStatus.SUCCESS);
            
            Response savedResponse = responseRepository.save(response);
            
            // Update query with completion details
            query.setStatus(Query.QueryStatus.COMPLETED);
            query.setResponseId(savedResponse.getId());
            query.setCompletedAt(LocalDateTime.now());
            query.setProcessingTimeMs(System.currentTimeMillis() - 
                    query.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
            queryRepository.save(query);
            
            logAction(query.getUserId(), query.getId(), "Query", 
                     AuditLog.ActionType.QUERY_PROCESSED, "Query processed successfully");
            
        } catch (Exception e) {
            log.error("Error processing query: {}", e.getMessage());
            query.setStatus(Query.QueryStatus.FAILED);
            queryRepository.save(query);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Cacheable(value = "queries", key = "#id")
    public QueryResponse getQueryById(String id) {
        Query query = queryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Query not found with id: " + id));
        
        QueryResponse.QueryResponseBuilder responseBuilder = QueryResponse.builder()
                .id(query.getId())
                .queryText(query.getQueryText())
                .status(query.getStatus().toString())
                .createdAt(query.getCreatedAt())
                .completedAt(query.getCompletedAt())
                .processingTimeMs(query.getProcessingTimeMs());
        
        // Get associated response if exists
        if (query.getResponseId() != null) {
            responseRepository.findById(query.getResponseId()).ifPresent(response -> {
                responseBuilder.responseText(response.getResponseText());
                responseBuilder.confidenceScore(response.getConfidenceScore());
            });
        }
        
        return responseBuilder.build();
    }
    
    @Cacheable(value = "queryHistory", key = "T(com.aiko.orchestrator.util.CacheKeyGenerator).generateKeyForPageable('history', #pageable, #userId)")
    public Page<QueryResponse> getQueryHistory(String userId, Pageable pageable) {
        Page<Query> queries;
        if (userId != null) {
            queries = queryRepository.findByUserId(userId, pageable);
        } else {
            queries = queryRepository.findAll(pageable);
        }
        
        return queries.map(this::convertToQueryResponse);
    }
    
    public void cancelQuery(String id) {
        Query query = queryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Query not found with id: " + id));
        
        if (query.getStatus() == Query.QueryStatus.PENDING || 
            query.getStatus() == Query.QueryStatus.PROCESSING) {
            query.setStatus(Query.QueryStatus.CANCELLED);
            queryRepository.save(query);
        }
    }
    
    private QueryResponse convertToQueryResponse(Query query) {
        return QueryResponse.builder()
                .id(query.getId())
                .queryText(query.getQueryText())
                .status(query.getStatus().toString())
                .createdAt(query.getCreatedAt())
                .completedAt(query.getCompletedAt())
                .processingTimeMs(query.getProcessingTimeMs())
                .build();
    }
    
    /**
     * Batch process multiple queries asynchronously
     * Returns the count of successfully submitted queries
     */
    @Async("queryTaskExecutor")
    public CompletableFuture<Integer> batchProcessQueries(List<String> queryIds) {
        log.info("Starting batch processing for {} queries", queryIds.size());
        int processedCount = 0;
        
        for (String queryId : queryIds) {
            try {
                Query query = queryRepository.findById(queryId)
                        .orElseThrow(() -> new ResourceNotFoundException("Query not found: " + queryId));
                
                if (query.getStatus() == Query.QueryStatus.PENDING) {
                    processQueryAsync(query);
                    processedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to process query {}: {}", queryId, e.getMessage());
            }
        }
        
        log.info("Batch processing initiated for {} queries", processedCount);
        return CompletableFuture.completedFuture(processedCount);
    }
    
    private void logAction(String userId, String entityId, String entityType, 
                          AuditLog.ActionType action, String description) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setEntityId(entityId);
        log.setEntityType(entityType);
        log.setAction(action);
        log.setDescription(description);
        auditLogRepository.save(log);
    }
}