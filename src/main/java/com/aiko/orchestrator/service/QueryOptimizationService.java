package com.aiko.orchestrator.service;

import com.mongodb.client.MongoCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryOptimizationService {
    
    private final MongoTemplate mongoTemplate;
    
    /**
     * Analyze query performance and provide optimization suggestions
     */
    public QueryAnalysisResult analyzeQuery(String collectionName, Query query) {
        log.debug("Analyzing query performance for collection: {}", collectionName);
        
        Document explainResult = mongoTemplate.execute(collectionName, mongoCollection -> {
            MongoCollection<Document> collection = (MongoCollection<Document>) mongoCollection;
            
            // Execute explain with execution stats
            Document command = new Document("find", collectionName)
                .append("filter", query.getQueryObject())
                .append("limit", query.getLimit())
                .append("skip", query.getSkip());
            
            if (query.getSortObject() != null) {
                command.append("sort", query.getSortObject());
            }
            
            Document explainCommand = new Document("explain", command)
                .append("verbosity", "executionStats");
            
            return mongoTemplate.getDb().runCommand(explainCommand);
        });
        
        return parseExplainResult(explainResult);
    }
    
    /**
     * Get slow queries from the MongoDB profiler
     */
    public List<SlowQuery> getSlowQueries(int limitMs) {
        List<SlowQuery> slowQueries = new ArrayList<>();
        
        // Enable profiling if not already enabled
        mongoTemplate.getDb().runCommand(new Document("profile", 1)
            .append("slowms", limitMs));
        
        // Query the system.profile collection for slow queries
        Query query = new Query();
        query.limit(100);
        
        mongoTemplate.execute("system.profile", mongoCollection -> {
            MongoCollection<Document> collection = (MongoCollection<Document>) mongoCollection;
            
            collection.find(new Document("millis", new Document("$gte", limitMs)))
                .sort(new Document("millis", -1))
                .limit(100)
                .forEach(doc -> {
                    SlowQuery slowQuery = new SlowQuery();
                    slowQuery.setNamespace(doc.getString("ns"));
                    slowQuery.setOperation(doc.getString("op"));
                    Integer millis = doc.getInteger("millis");
                    slowQuery.setMillis(millis != null ? millis : 0);
                    slowQuery.setCommand(doc.get("command", Document.class));
                    slowQuery.setTimestamp(doc.getDate("ts"));
                    slowQueries.add(slowQuery);
                });
            
            return null;
        });
        
        return slowQueries;
    }
    
    /**
     * Suggest indexes based on query patterns
     */
    public List<IndexSuggestion> suggestIndexes(String collectionName) {
        List<IndexSuggestion> suggestions = new ArrayList<>();
        
        // Analyze slow queries
        List<SlowQuery> slowQueries = getSlowQueries(100);
        
        for (SlowQuery slowQuery : slowQueries) {
            if (slowQuery.getNamespace() != null && 
                slowQuery.getNamespace().endsWith("." + collectionName)) {
                
                Document command = slowQuery.getCommand();
                if (command != null && command.containsKey("filter")) {
                    Document filter = command.get("filter", Document.class);
                    
                    IndexSuggestion suggestion = new IndexSuggestion();
                    suggestion.setCollection(collectionName);
                    suggestion.setFields(filter.keySet());
                    suggestion.setReason("Slow query detected - " + slowQuery.getMillis() + "ms");
                    suggestion.setPriority(calculatePriority(slowQuery.getMillis()));
                    
                    suggestions.add(suggestion);
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * Optimize a specific query by rewriting it
     */
    public Query optimizeQuery(Query originalQuery) {
        Query optimized = new Query();
        
        // Copy criteria
        if (originalQuery.getQueryObject() != null) {
            // Convert Document to Criteria
            originalQuery.getQueryObject().forEach((key, value) -> {
                optimized.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where(key).is(value));
            });
        }
        
        // Add hints for index usage if beneficial
        if (shouldUseHint(originalQuery)) {
            optimized.withHint("{ userId: 1, createdAt: -1 }");
        }
        
        // Optimize projections - only include necessary fields
        if (originalQuery.getFieldsObject() == null || 
            originalQuery.getFieldsObject().isEmpty()) {
            // Add projection to reduce data transfer
            optimized.fields()
                .include("id")
                .include("userId")
                .include("status")
                .include("createdAt");
        }
        
        // Copy other properties
        optimized.limit(originalQuery.getLimit());
        optimized.skip(originalQuery.getSkip());
        if (originalQuery.getSortObject() != null) {
            // Copy sort by reconstructing it
            originalQuery.getSortObject().forEach((key, value) -> {
                int direction = value instanceof Integer ? (Integer) value : 1;
                if (direction == 1) {
                    optimized.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, key));
                } else {
                    optimized.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, key));
                }
            });
        }
        
        return optimized;
    }
    
    private QueryAnalysisResult parseExplainResult(Document explainResult) {
        QueryAnalysisResult result = new QueryAnalysisResult();
        
        try {
            Document executionStats = explainResult.get("executionStats", Document.class);
            
            if (executionStats != null) {
                result.setExecutionTimeMs(getIntegerValue(executionStats, "executionTimeMillis", 0));
                result.setTotalDocsExamined(getIntegerValue(executionStats, "totalDocsExamined", 0));
                result.setTotalKeysExamined(getIntegerValue(executionStats, "totalKeysExamined", 0));
                result.setNReturned(getIntegerValue(executionStats, "nReturned", 0));
                
                // Check if index was used
                Document executionStages = executionStats.get("executionStages", Document.class);
                if (executionStages != null) {
                    String stage = executionStages.getString("stage");
                    if (stage != null) {
                        result.setIndexUsed(stage.contains("IXSCAN"));
                        result.setStage(stage);
                        
                        if (stage.equals("COLLSCAN")) {
                            result.addOptimizationTip("Collection scan detected! Add an index for better performance.");
                        }
                    }
                }
                
                // Calculate efficiency
                double efficiency = calculateEfficiency(
                    result.getTotalDocsExamined(), 
                    result.getNReturned()
                );
                result.setEfficiencyScore(efficiency);
                
                // Add optimization tips based on analysis
                if (result.getExecutionTimeMs() > 1000) {
                    result.addOptimizationTip("Query took more than 1 second. Consider optimization.");
                }
                
                if (result.getTotalDocsExamined() > result.getNReturned() * 10) {
                    result.addOptimizationTip("Examining too many documents. Consider adding a more selective index.");
                }
            }
        } catch (Exception e) {
            log.error("Error parsing explain result", e);
        }
        
        return result;
    }
    
    private int getIntegerValue(Document doc, String key, int defaultValue) {
        Object value = doc.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        return defaultValue;
    }
    
    private boolean shouldUseHint(Query query) {
        // Logic to determine if a hint would be beneficial
        return query.getQueryObject() != null && 
               query.getQueryObject().containsKey("userId");
    }
    
    private double calculateEfficiency(int docsExamined, int docsReturned) {
        if (docsExamined == 0) return 1.0;
        return (double) docsReturned / docsExamined;
    }
    
    private String calculatePriority(int millis) {
        if (millis > 5000) return "HIGH";
        if (millis > 1000) return "MEDIUM";
        return "LOW";
    }
    
    // Inner classes for results
    public static class QueryAnalysisResult {
        private int executionTimeMs;
        private int totalDocsExamined;
        private int totalKeysExamined;
        private int nReturned;
        private boolean indexUsed;
        private String stage;
        private double efficiencyScore;
        private List<String> optimizationTips = new ArrayList<>();
        
        // Getters and setters
        public void addOptimizationTip(String tip) {
            this.optimizationTips.add(tip);
        }
        
        public int getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(int executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        
        public int getTotalDocsExamined() { return totalDocsExamined; }
        public void setTotalDocsExamined(int totalDocsExamined) { this.totalDocsExamined = totalDocsExamined; }
        
        public int getTotalKeysExamined() { return totalKeysExamined; }
        public void setTotalKeysExamined(int totalKeysExamined) { this.totalKeysExamined = totalKeysExamined; }
        
        public int getNReturned() { return nReturned; }
        public void setNReturned(int nReturned) { this.nReturned = nReturned; }
        
        public boolean isIndexUsed() { return indexUsed; }
        public void setIndexUsed(boolean indexUsed) { this.indexUsed = indexUsed; }
        
        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }
        
        public double getEfficiencyScore() { return efficiencyScore; }
        public void setEfficiencyScore(double efficiencyScore) { this.efficiencyScore = efficiencyScore; }
        
        public List<String> getOptimizationTips() { return optimizationTips; }
        public void setOptimizationTips(List<String> optimizationTips) { this.optimizationTips = optimizationTips; }
    }
    
    public static class SlowQuery {
        private String namespace;
        private String operation;
        private Integer millis;
        private Document command;
        private Date timestamp;
        
        // Getters and setters
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        
        public Integer getMillis() { return millis; }
        public void setMillis(Integer millis) { this.millis = millis; }
        
        public Document getCommand() { return command; }
        public void setCommand(Document command) { this.command = command; }
        
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    }
    
    public static class IndexSuggestion {
        private String collection;
        private Set<String> fields;
        private String reason;
        private String priority;
        
        // Getters and setters
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        
        public Set<String> getFields() { return fields; }
        public void setFields(Set<String> fields) { this.fields = fields; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
}
