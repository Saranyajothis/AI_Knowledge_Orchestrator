package com.aiko.orchestrator.service;

import com.aiko.orchestrator.model.Query;
import com.aiko.orchestrator.model.Query.QueryType;
import com.aiko.orchestrator.model.Response;
import com.aiko.orchestrator.model.AuditLog;
import com.aiko.orchestrator.model.Knowledge;
import com.aiko.orchestrator.repository.QueryRepository;
import com.aiko.orchestrator.repository.ResponseRepository;
import com.aiko.orchestrator.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {
    
    private final QueryRepository queryRepository;
    private final ResponseRepository responseRepository;
    private final AuditLogRepository auditLogRepository;
    private final KnowledgeService knowledgeService;
    private final Random random = new Random();
    
    /**
     * Main orchestration method - routes queries to appropriate agents
     * This replaces the simple mock processing in QueryService
     * Now uses dedicated orchestration thread pool for better performance
     */
    @Async("orchestrationTaskExecutor")
    public CompletableFuture<Response> orchestrateQuery(Query query) {
        log.info("Starting orchestration for query: {} of type: {}", 
                query.getId(), query.getQueryType());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Update status to PROCESSING
            query.setStatus(Query.QueryStatus.PROCESSING);
            queryRepository.save(query);
            
            // Route to appropriate agent based on query type
            Response response = routeToAgent(query);
            
            // Save the response
            Response savedResponse = responseRepository.save(response);
            
            // Update query with completion details
            long processingTime = System.currentTimeMillis() - startTime;
            query.setStatus(Query.QueryStatus.COMPLETED);
            query.setResponseId(savedResponse.getId());
            query.setCompletedAt(LocalDateTime.now());
            query.setProcessingTimeMs(processingTime);
            queryRepository.save(query);
            
            // Log successful processing
            logAction(query.getUserId(), query.getId(), "Query", 
                     AuditLog.ActionType.QUERY_PROCESSED, 
                     String.format("Query processed by %s in %dms", 
                                  response.getAgentType(), processingTime));
            
            log.info("Query {} completed in {}ms with confidence: {}", 
                    query.getId(), processingTime, response.getConfidenceScore());
            
            return CompletableFuture.completedFuture(savedResponse);
            
        } catch (Exception e) {
            log.error("Orchestration failed for query {}: {}", query.getId(), e.getMessage());
            
            // Mark query as failed
            query.setStatus(Query.QueryStatus.FAILED);
            queryRepository.save(query);
            
            // Log failure
            logAction(query.getUserId(), query.getId(), "Query", 
                     AuditLog.ActionType.ERROR, 
                     "Query processing failed: " + e.getMessage());
            
            // Create error response
            Response errorResponse = createErrorResponse(query, e);
            return CompletableFuture.completedFuture(errorResponse);
        }
    }
    
    /**
     * Route query to appropriate agent based on type
     */
    private Response routeToAgent(Query query) {
        log.debug("Routing query {} to appropriate agent", query.getId());
        
        return switch (query.getQueryType()) {
            case RESEARCH -> processResearchQuery(query);
            case CODE -> processCodeQuery(query);
            case DECISION -> processDecisionQuery(query);
            case GENERAL -> processGeneralQuery(query);
        };
    }
    
    /**
     * Process RESEARCH type queries
     * Searches knowledge base and generates research-based response
     */
    private Response processResearchQuery(Query query) {
        log.debug("Processing RESEARCH query: {}", query.getId());
        
        // Search knowledge base for relevant documents
        List<Knowledge> relevantDocs = knowledgeService.searchKnowledge(
            query.getQueryText(), 5
        );
        
        // Create response
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("RESEARCH_AGENT");
        response.setModelUsed("gpt-4-research");
        
        // Build research response based on found documents
        StringBuilder researchResult = new StringBuilder();
        researchResult.append("Based on my research analysis:\n\n");
        
        if (!relevantDocs.isEmpty()) {
            researchResult.append("📚 Found ").append(relevantDocs.size())
                         .append(" relevant documents in the knowledge base.\n\n");
            
            researchResult.append("Key Findings:\n");
            researchResult.append("• Primary sources indicate strong evidence\n");
            researchResult.append("• Multiple documents confirm this information\n");
            researchResult.append("• Cross-referenced with internal knowledge base\n\n");
            
            // Add document references
            researchResult.append("References:\n");
            for (int i = 0; i < Math.min(3, relevantDocs.size()); i++) {
                Knowledge doc = relevantDocs.get(i);
                researchResult.append(String.format("%d. %s\n", i + 1, doc.getTitle()));
            }
            
            // Store document IDs as sources
            response.setSources(relevantDocs.stream()
                              .map(Knowledge::getId)
                              .limit(5)
                              .toList());
        } else {
            researchResult.append("No existing knowledge found in database.\n");
            researchResult.append("Generating response based on general knowledge:\n\n");
            researchResult.append("• Analysis based on query context\n");
            researchResult.append("• Inference from related topics\n");
            researchResult.append("• General domain expertise applied\n");
        }
        
        response.setResponseText(researchResult.toString());
        response.setConfidenceScore(relevantDocs.isEmpty() ? 0.65 : 0.75 + (relevantDocs.size() * 0.04));
        response.setStatus(Response.ResponseStatus.SUCCESS);
        response.setTokensUsed(random.nextInt(500) + 200);
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documents_found", relevantDocs.size());
        metadata.put("search_depth", "comprehensive");
        metadata.put("knowledge_base_used", true);
        response.setMetadata(metadata);
        
        return response;
    }
    
    /**
     * Process CODE type queries
     * Generates code solutions and explanations
     */
    private Response processCodeQuery(Query query) {
        log.debug("Processing CODE query: {}", query.getId());
        
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("CODE_AGENT");
        response.setModelUsed("codex-2024");
        
        // Detect programming context
        String queryLower = query.getQueryText().toLowerCase();
        String language = detectProgrammingLanguage(queryLower);
        
        // Generate code response
        StringBuilder codeResponse = new StringBuilder();
        codeResponse.append("## Code Solution\n\n");
        codeResponse.append("Based on your request: \"").append(query.getQueryText()).append("\"\n\n");
        
        codeResponse.append("```").append(language).append("\n");
        
        // Generate mock code based on query
        if (queryLower.contains("sort") || queryLower.contains("array")) {
            codeResponse.append("// Solution for array/sorting operation\n");
            codeResponse.append("public int[] sortArray(int[] nums) {\n");
            codeResponse.append("    // Using efficient sorting algorithm\n");
            codeResponse.append("    Arrays.sort(nums);\n");
            codeResponse.append("    return nums;\n");
            codeResponse.append("}\n");
        } else if (queryLower.contains("api") || queryLower.contains("rest")) {
            codeResponse.append("// REST API endpoint implementation\n");
            codeResponse.append("@GetMapping(\"/api/data\")\n");
            codeResponse.append("public ResponseEntity<List<Data>> getData() {\n");
            codeResponse.append("    List<Data> data = service.fetchData();\n");
            codeResponse.append("    return ResponseEntity.ok(data);\n");
            codeResponse.append("}\n");
        } else {
            codeResponse.append("// Generic solution implementation\n");
            codeResponse.append("public Result processData(Input input) {\n");
            codeResponse.append("    // Validate input\n");
            codeResponse.append("    if (input == null) return Result.empty();\n");
            codeResponse.append("    \n");
            codeResponse.append("    // Process data\n");
            codeResponse.append("    Result result = processor.execute(input);\n");
            codeResponse.append("    return result;\n");
            codeResponse.append("}\n");
        }
        
        codeResponse.append("```\n\n");
        
        codeResponse.append("### Explanation:\n");
        codeResponse.append("• The solution follows best practices and SOLID principles\n");
        codeResponse.append("• Time Complexity: O(n log n) for sorting operations\n");
        codeResponse.append("• Space Complexity: O(1) for in-place operations\n");
        codeResponse.append("• Error handling included for edge cases\n\n");
        
        codeResponse.append("### Usage Example:\n");
        codeResponse.append("```").append(language).append("\n");
        codeResponse.append("// Example usage\n");
        codeResponse.append("Result output = processData(inputData);\n");
        codeResponse.append("System.out.println(output);\n");
        codeResponse.append("```\n");
        
        response.setResponseText(codeResponse.toString());
        response.setConfidenceScore(0.80 + random.nextDouble() * 0.15);
        response.setStatus(Response.ResponseStatus.SUCCESS);
        response.setTokensUsed(random.nextInt(800) + 300);
        
        // Add code-specific metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("language", language);
        metadata.put("lines_of_code", 15 + random.nextInt(20));
        metadata.put("complexity", "medium");
        metadata.put("syntax_valid", true);
        response.setMetadata(metadata);
        
        return response;
    }
    
    /**
     * Process DECISION type queries
     * Analyzes options and provides recommendations
     */
    private Response processDecisionQuery(Query query) {
        log.debug("Processing DECISION query: {}", query.getId());
        
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("DECISION_AGENT");
        response.setModelUsed("claude-3-decision");
        
        StringBuilder decisionResponse = new StringBuilder();
        decisionResponse.append("# Decision Analysis Report\n\n");
        decisionResponse.append("Query: \"").append(query.getQueryText()).append("\"\n\n");
        
        decisionResponse.append("## Options Analysis\n\n");
        
        decisionResponse.append("### Option A: Conservative Approach\n");
        decisionResponse.append("**Pros:**\n");
        decisionResponse.append("• Lower risk profile\n");
        decisionResponse.append("• Proven track record\n");
        decisionResponse.append("• Easier implementation\n\n");
        decisionResponse.append("**Cons:**\n");
        decisionResponse.append("• Limited scalability\n");
        decisionResponse.append("• May become outdated\n\n");
        
        decisionResponse.append("### Option B: Progressive Approach\n");
        decisionResponse.append("**Pros:**\n");
        decisionResponse.append("• Future-proof solution\n");
        decisionResponse.append("• Better performance potential\n");
        decisionResponse.append("• Competitive advantage\n\n");
        decisionResponse.append("**Cons:**\n");
        decisionResponse.append("• Higher initial investment\n");
        decisionResponse.append("• Steeper learning curve\n\n");
        
        decisionResponse.append("## Recommendation\n");
        decisionResponse.append("Based on comprehensive analysis, **Option B** is recommended.\n\n");
        
        decisionResponse.append("**Rationale:**\n");
        decisionResponse.append("• Long-term benefits outweigh short-term challenges\n");
        decisionResponse.append("• Aligns with industry trends and best practices\n");
        decisionResponse.append("• Provides flexibility for future adaptations\n\n");
        
        decisionResponse.append("## Implementation Strategy\n");
        decisionResponse.append("1. Start with pilot implementation\n");
        decisionResponse.append("2. Gather metrics and feedback\n");
        decisionResponse.append("3. Iterate based on learnings\n");
        decisionResponse.append("4. Scale gradually with monitoring\n");
        
        response.setResponseText(decisionResponse.toString());
        response.setConfidenceScore(0.70 + random.nextDouble() * 0.20);
        response.setStatus(Response.ResponseStatus.SUCCESS);
        response.setTokensUsed(random.nextInt(600) + 400);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("options_analyzed", 2);
        metadata.put("decision_framework", "cost-benefit analysis");
        metadata.put("confidence_level", "high");
        response.setMetadata(metadata);
        
        return response;
    }
    
    /**
     * Process GENERAL type queries
     * Attempts to infer type or provides general response
     */
    private Response processGeneralQuery(Query query) {
        log.debug("Processing GENERAL query: {}", query.getId());
        
        // Try to infer the actual type
        QueryType inferredType = inferQueryType(query.getQueryText());
        
        if (inferredType != QueryType.GENERAL) {
            log.info("Re-categorizing general query as: {}", inferredType);
            query.setQueryType(inferredType);
            return routeToAgent(query);
        }
        
        // Process as general query
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("GENERAL_AGENT");
        response.setModelUsed("gpt-4-turbo");
        
        String responseText = String.format(
            "I understand your query: \"%s\"\n\n" +
            "Here's a comprehensive response:\n\n" +
            "Based on the available information and analysis, " +
            "I can provide the following insights:\n\n" +
            "• Key point 1: Relevant context and explanation\n" +
            "• Key point 2: Supporting details and evidence\n" +
            "• Key point 3: Additional considerations\n\n" +
            "This response addresses the main aspects of your query. " +
            "For more specific information, please provide additional context.",
            query.getQueryText()
        );
        
        response.setResponseText(responseText);
        response.setConfidenceScore(0.75);
        response.setStatus(Response.ResponseStatus.SUCCESS);
        response.setTokensUsed(random.nextInt(400) + 200);
        
        return response;
    }
    
    /**
     * Attempt to infer query type from text
     */
    private QueryType inferQueryType(String queryText) {
        String lower = queryText.toLowerCase();
        
        // Code indicators
        if (lower.contains("code") || lower.contains("function") || 
            lower.contains("implement") || lower.contains("algorithm") ||
            lower.contains("program") || lower.contains("script")) {
            return QueryType.CODE;
        }
        
        // Research indicators
        if (lower.contains("what is") || lower.contains("explain") ||
            lower.contains("how does") || lower.contains("define") ||
            lower.contains("research") || lower.contains("tell me about")) {
            return QueryType.RESEARCH;
        }
        
        // Decision indicators
        if (lower.contains("should i") || lower.contains("which") ||
            lower.contains("better") || lower.contains("recommend") ||
            lower.contains("choose") || lower.contains("decide")) {
            return QueryType.DECISION;
        }
        
        return QueryType.GENERAL;
    }
    
    /**
     * Detect programming language from query
     */
    private String detectProgrammingLanguage(String queryText) {
        if (queryText.contains("java")) return "java";
        if (queryText.contains("python")) return "python";
        if (queryText.contains("javascript") || queryText.contains("js")) return "javascript";
        if (queryText.contains("typescript")) return "typescript";
        if (queryText.contains("sql")) return "sql";
        if (queryText.contains("c++")) return "cpp";
        if (queryText.contains("react")) return "jsx";
        return "java"; // default
    }
    
    /**
     * Create error response
     */
    private Response createErrorResponse(Query query, Exception e) {
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("ERROR_HANDLER");
        response.setResponseText("An error occurred while processing your query: " + e.getMessage());
        response.setConfidenceScore(0.0);
        response.setStatus(Response.ResponseStatus.ERROR);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("error_type", e.getClass().getSimpleName());
        metadata.put("error_message", e.getMessage());
        response.setMetadata(metadata);
        
        return responseRepository.save(response);
    }
    
    /**
     * Asynchronously communicate with external AI agents
     * Simulates calls to OpenAI, Claude, or other AI services
     */
    @Async("orchestrationTaskExecutor")
    public CompletableFuture<String> callExternalAgent(String agentType, String prompt) {
        log.info("Calling external agent {} on thread: {}", 
                agentType, Thread.currentThread().getName());
        
        try {
            // Simulate API call delay
            Thread.sleep(1000 + random.nextInt(2000));
            
            // Mock response from external agent
            String response = String.format(
                "Response from %s:\n" +
                "Based on the prompt: '%s'\n" +
                "Generated content with high confidence.\n" +
                "Response ID: %s",
                agentType, 
                prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt,
                UUID.randomUUID().toString()
            );
            
            log.info("External agent {} responded successfully", agentType);
            return CompletableFuture.completedFuture(response);
            
        } catch (InterruptedException e) {
            log.error("External agent call interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent communication failed", e);
        }
    }
    
    /**
     * Batch orchestrate multiple queries asynchronously
     * Processes multiple queries in parallel using the orchestration thread pool
     */
    @Async("orchestrationTaskExecutor")
    public CompletableFuture<Map<String, Response>> batchOrchestrate(List<Query> queries) {
        log.info("Starting batch orchestration for {} queries on thread: {}", 
                queries.size(), Thread.currentThread().getName());
        
        Map<String, Response> responses = new HashMap<>();
        List<CompletableFuture<Response>> futures = new ArrayList<>();
        
        // Process each query asynchronously
        for (Query query : queries) {
            CompletableFuture<Response> future = orchestrateQuery(query)
                .exceptionally(ex -> {
                    log.error("Failed to orchestrate query {}: {}", 
                            query.getId(), ex.getMessage());
                    return createErrorResponse(query, new Exception(ex));
                });
            futures.add(future);
        }
        
        // Wait for all queries to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
        
        // Collect results
        for (int i = 0; i < queries.size(); i++) {
            Query query = queries.get(i);
            try {
                Response response = futures.get(i).get(5, TimeUnit.SECONDS);
                responses.put(query.getId(), response);
            } catch (Exception e) {
                log.error("Failed to get response for query {}: {}", 
                        query.getId(), e.getMessage());
                responses.put(query.getId(), createErrorResponse(query, e));
            }
        }
        
        log.info("Batch orchestration completed for {} queries", responses.size());
        return CompletableFuture.completedFuture(responses);
    }
    
    /**
     * Asynchronously synthesize responses from multiple agents
     * Combines and ranks responses from different AI agents
     */
    @Async("orchestrationTaskExecutor")
    public CompletableFuture<Response> synthesizeResponses(Query query, List<String> agentTypes) {
        log.info("Synthesizing responses from {} agents for query {} on thread: {}", 
                agentTypes.size(), query.getId(), Thread.currentThread().getName());
        
        List<CompletableFuture<String>> agentFutures = new ArrayList<>();
        
        // Call all agents in parallel
        for (String agentType : agentTypes) {
            CompletableFuture<String> future = callExternalAgent(agentType, query.getQueryText());
            agentFutures.add(future);
        }
        
        // Wait for all agents to respond
        CompletableFuture.allOf(agentFutures.toArray(new CompletableFuture[0]))
            .join();
        
        // Collect and synthesize responses
        StringBuilder synthesized = new StringBuilder();
        synthesized.append("## Synthesized Multi-Agent Response\n\n");
        
        double totalConfidence = 0;
        int successfulAgents = 0;
        
        for (int i = 0; i < agentTypes.size(); i++) {
            try {
                String agentResponse = agentFutures.get(i).get(1, TimeUnit.SECONDS);
                synthesized.append("### ").append(agentTypes.get(i)).append(" Analysis:\n");
                synthesized.append(agentResponse).append("\n\n");
                successfulAgents++;
                totalConfidence += 0.7 + random.nextDouble() * 0.3;
            } catch (Exception e) {
                log.warn("Agent {} failed to respond: {}", agentTypes.get(i), e.getMessage());
                synthesized.append("### ").append(agentTypes.get(i)).append(": No response\n\n");
            }
        }
        
        // Create final response
        Response response = new Response();
        response.setQueryId(query.getId());
        response.setAgentType("MULTI_AGENT_SYNTHESIS");
        response.setModelUsed(String.join("+", agentTypes));
        response.setResponseText(synthesized.toString());
        response.setConfidenceScore(successfulAgents > 0 ? totalConfidence / successfulAgents : 0.5);
        response.setStatus(successfulAgents > 0 ? 
                         Response.ResponseStatus.SUCCESS : 
                         Response.ResponseStatus.PARTIAL);
        response.setTokensUsed(random.nextInt(1000) + 500);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agents_used", agentTypes.size());
        metadata.put("agents_succeeded", successfulAgents);
        metadata.put("synthesis_method", "parallel_aggregation");
        response.setMetadata(metadata);
        
        Response savedResponse = responseRepository.save(response);
        
        log.info("Response synthesis completed with {} successful agents", successfulAgents);
        return CompletableFuture.completedFuture(savedResponse);
    }
    
    /**
     * Log action to audit log
     */
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