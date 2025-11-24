package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.model.Knowledge;
import com.aiko.orchestrator.model.Query;
import com.aiko.orchestrator.model.Response;
import com.aiko.orchestrator.service.KnowledgeService;
import com.aiko.orchestrator.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Controller for testing async processing features
 * This controller demonstrates the async capabilities of the system
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/async-test")
@RequiredArgsConstructor
public class TestAsyncController {

    private final KnowledgeService knowledgeService;
    private final OrchestrationService orchestrationService;

    /**
     * Test async document processing
     * Triggers async processing of a document and returns immediately
     */
    @PostMapping("/process-document/{documentId}")
    public ResponseEntity<Map<String, Object>> testAsyncDocumentProcessing(
            @PathVariable String documentId) {
        
        log.info("Starting async processing for document: {}", documentId);
        
        // Start async processing - returns immediately
        CompletableFuture<Knowledge> future = knowledgeService.processDocumentAsync(documentId);
        
        // Add callback to log when processing is complete
        future.thenAccept(document -> 
            log.info("Document {} processing completed with status: {}", 
                    document.getId(), document.getProcessingStatus())
        ).exceptionally(ex -> {
            log.error("Document processing failed: {}", ex.getMessage());
            return null;
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document processing started asynchronously");
        response.put("documentId", documentId);
        response.put("status", "PROCESSING_INITIATED");
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Test batch document processing
     * Processes multiple documents in parallel
     */
    @PostMapping("/batch-process")
    public ResponseEntity<Map<String, Object>> testBatchProcessing(
            @RequestBody List<String> documentIds) {
        
        log.info("Starting batch processing for {} documents", documentIds.size());
        
        CompletableFuture<Integer> future = knowledgeService.batchProcessDocuments(documentIds);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Batch processing initiated");
        response.put("documentsQueued", documentIds.size());
        response.put("documentIds", documentIds);
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Test large document ingestion
     * Demonstrates async handling of large file uploads
     */
    @PostMapping("/ingest-large-document")
    public ResponseEntity<Map<String, Object>> testLargeDocumentIngestion(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "tags", required = false) List<String> tags) {
        
        log.info("Starting async ingestion of large document: {} ({}MB)", 
                title, file.getSize() / (1024.0 * 1024.0));
        
        CompletableFuture<Knowledge> future = knowledgeService.ingestLargeDocumentAsync(
                file, title, tags != null ? tags : new ArrayList<>());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Large document ingestion started");
        response.put("title", title);
        response.put("fileSize", file.getSize());
        response.put("status", "INGESTION_STARTED");
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Test search index update
     * Triggers async reindexing of all pending documents
     */
    @PostMapping("/update-search-index")
    public ResponseEntity<Map<String, Object>> testSearchIndexUpdate() {
        
        log.info("Triggering async search index update");
        
        CompletableFuture<Void> future = knowledgeService.updateSearchIndexAsync();
        
        future.thenRun(() -> 
            log.info("Search index update completed successfully")
        ).exceptionally(ex -> {
            log.error("Search index update failed: {}", ex.getMessage());
            return null;
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Search index update initiated");
        response.put("status", "INDEX_UPDATE_STARTED");
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Test async orchestration with external agents
     * Demonstrates calling multiple AI agents in parallel
     */
    @PostMapping("/test-multi-agent")
    public ResponseEntity<Map<String, Object>> testMultiAgentOrchestration(
            @RequestBody Map<String, String> request) {
        
        String queryText = request.get("query");
        List<String> agentTypes = Arrays.asList("GPT-4", "CLAUDE", "GEMINI");
        
        log.info("Testing multi-agent orchestration with {} agents", agentTypes.size());
        
        // Create a test query
        Query query = new Query();
        query.setId(UUID.randomUUID().toString());
        query.setQueryText(queryText);
        query.setQueryType(Query.QueryType.GENERAL);
        
        // Start async synthesis
        CompletableFuture<Response> future = orchestrationService.synthesizeResponses(query, agentTypes);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Multi-agent synthesis started");
        response.put("queryId", query.getId());
        response.put("agents", agentTypes);
        response.put("status", "SYNTHESIS_IN_PROGRESS");
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Test calling external agent
     * Simulates async communication with AI service
     */
    @PostMapping("/call-agent")
    public ResponseEntity<Map<String, Object>> testExternalAgentCall(
            @RequestBody Map<String, String> request) {
        
        String agentType = request.getOrDefault("agent", "GPT-4");
        String prompt = request.get("prompt");
        
        log.info("Calling external agent: {}", agentType);
        
        CompletableFuture<String> future = orchestrationService.callExternalAgent(agentType, prompt);
        
        // Add timeout and error handling
        future.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
              .thenAccept(response -> 
                  log.info("Agent {} responded successfully", agentType)
              )
              .exceptionally(ex -> {
                  log.error("Agent call failed: {}", ex.getMessage());
                  return null;
              });
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "External agent call initiated");
        response.put("agent", agentType);
        response.put("status", "CALLING_AGENT");
        response.put("timestamp", new Date());
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Get async processing status
     * Check the status of async operations
     */
    @GetMapping("/status/{documentId}")
    public ResponseEntity<Map<String, Object>> getProcessingStatus(
            @PathVariable String documentId) {
        
        // This would typically query the document status
        // For demonstration, returning mock status
        
        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("status", "PROCESSING");
        response.put("progress", "65%");
        response.put("estimatedCompletion", "2 minutes");
        response.put("timestamp", new Date());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test async with wait
     * Demonstrates waiting for async result with timeout
     */
    @PostMapping("/process-and-wait/{documentId}")
    public ResponseEntity<Map<String, Object>> processAndWait(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "5") int timeoutSeconds) {
        
        log.info("Processing document {} with {}s timeout", documentId, timeoutSeconds);
        
        CompletableFuture<Knowledge> future = knowledgeService.processDocumentAsync(documentId);
        
        try {
            // Wait for result with timeout
            Knowledge result = future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Processing completed");
            response.put("document", result);
            response.put("processingTime", 
                    java.time.Duration.between(result.getProcessingStartTime(), 
                                              result.getProcessingEndTime()).toMillis() + "ms");
            
            return ResponseEntity.ok(response);
            
        } catch (java.util.concurrent.TimeoutException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Processing still in progress after timeout");
            response.put("documentId", documentId);
            response.put("status", "TIMEOUT");
            response.put("timeoutSeconds", timeoutSeconds);
            
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error waiting for processing result: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Processing failed");
            response.put("error", e.getMessage());
            response.put("documentId", documentId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Monitor thread pool status
     * Returns information about async thread pools
     */
    @GetMapping("/thread-pool-status")
    public ResponseEntity<Map<String, Object>> getThreadPoolStatus() {
        
        // In a real implementation, you would inject ThreadPoolTaskExecutor beans
        // and query their status. This is a mock response for demonstration.
        
        Map<String, Object> status = new HashMap<>();
        
        Map<String, Object> queryPool = new HashMap<>();
        queryPool.put("corePoolSize", 4);
        queryPool.put("maxPoolSize", 10);
        queryPool.put("activeThreads", 2);
        queryPool.put("queuedTasks", 5);
        status.put("queryTaskExecutor", queryPool);
        
        Map<String, Object> knowledgePool = new HashMap<>();
        knowledgePool.put("corePoolSize", 3);
        knowledgePool.put("maxPoolSize", 8);
        knowledgePool.put("activeThreads", 1);
        knowledgePool.put("queuedTasks", 3);
        status.put("knowledgeTaskExecutor", knowledgePool);
        
        Map<String, Object> orchestrationPool = new HashMap<>();
        orchestrationPool.put("corePoolSize", 5);
        orchestrationPool.put("maxPoolSize", 15);
        orchestrationPool.put("activeThreads", 4);
        orchestrationPool.put("queuedTasks", 10);
        status.put("orchestrationTaskExecutor", orchestrationPool);
        
        status.put("timestamp", new Date());
        
        return ResponseEntity.ok(status);
    }
}
