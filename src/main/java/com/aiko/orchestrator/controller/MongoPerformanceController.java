package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.service.MongoBackupService;
import com.aiko.orchestrator.service.QueryOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/mongodb")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "MongoDB Performance", description = "MongoDB performance and backup management")
public class MongoPerformanceController {
    
    private final QueryOptimizationService queryOptimizationService;
    private final MongoBackupService mongoBackupService;
    private final MongoTemplate mongoTemplate;
    
    @GetMapping("/stats")
    @Operation(summary = "Get MongoDB statistics")
    public ResponseEntity<Map<String, Object>> getMongoStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get database stats
        org.bson.Document dbStats = mongoTemplate.getDb()
            .runCommand(new org.bson.Document("dbStats", 1));
        
        stats.put("database", mongoTemplate.getDb().getName());
        stats.put("collections", mongoTemplate.getCollectionNames().size());
        stats.put("dataSize", dbStats.get("dataSize"));
        stats.put("storageSize", dbStats.get("storageSize"));
        stats.put("indexes", dbStats.get("indexes"));
        stats.put("indexSize", dbStats.get("indexSize"));
        
        return ResponseEntity.ok(stats);
    }
    
    @PostMapping("/analyze-query")
    @Operation(summary = "Analyze query performance")
    public ResponseEntity<QueryOptimizationService.QueryAnalysisResult> analyzeQuery(
            @RequestParam String collection,
            @RequestBody Map<String, Object> queryFilter) {
        
        Query query = new Query();
        // Build query from filter (simplified)
        queryFilter.forEach((key, value) -> 
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where(key).is(value))
        );
        
        QueryOptimizationService.QueryAnalysisResult result = 
            queryOptimizationService.analyzeQuery(collection, query);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/slow-queries")
    @Operation(summary = "Get slow queries")
    public ResponseEntity<List<QueryOptimizationService.SlowQuery>> getSlowQueries(
            @RequestParam(defaultValue = "100") int thresholdMs) {
        
        List<QueryOptimizationService.SlowQuery> slowQueries = 
            queryOptimizationService.getSlowQueries(thresholdMs);
        
        return ResponseEntity.ok(slowQueries);
    }
    
    @GetMapping("/index-suggestions/{collection}")
    @Operation(summary = "Get index suggestions for a collection")
    public ResponseEntity<List<QueryOptimizationService.IndexSuggestion>> getIndexSuggestions(
            @PathVariable String collection) {
        
        List<QueryOptimizationService.IndexSuggestion> suggestions = 
            queryOptimizationService.suggestIndexes(collection);
        
        return ResponseEntity.ok(suggestions);
    }
    
    @PostMapping("/backup")
    @Operation(summary = "Trigger manual backup")
    public ResponseEntity<MongoBackupService.BackupResult> triggerBackup(
            @RequestParam(defaultValue = "manual") String backupType) {
        
        log.info("Manual backup triggered by admin");
        MongoBackupService.BackupResult result = mongoBackupService.performBackup(backupType);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    @GetMapping("/backups")
    @Operation(summary = "List available backups")
    public ResponseEntity<List<MongoBackupService.BackupInfo>> listBackups() {
        List<MongoBackupService.BackupInfo> backups = mongoBackupService.listBackups();
        return ResponseEntity.ok(backups);
    }
    
    @PostMapping("/restore")
    @Operation(summary = "Restore from backup")
    public ResponseEntity<MongoBackupService.RestoreResult> restoreBackup(
            @RequestParam String timestamp) {
        
        log.warn("Database restore triggered by admin for timestamp: {}", timestamp);
        MongoBackupService.RestoreResult result = mongoBackupService.restoreBackup(timestamp);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    @DeleteMapping("/backup/{timestamp}")
    @Operation(summary = "Delete a specific backup")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable String timestamp) {
        boolean deleted = mongoBackupService.deleteBackup(timestamp);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", deleted);
        response.put("timestamp", timestamp);
        response.put("message", deleted ? "Backup deleted successfully" : "Backup not found");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/connection-pool")
    @Operation(summary = "Get connection pool statistics")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStats() {
        Map<String, Object> poolStats = new HashMap<>();
        
        // Get server status for connection info
        org.bson.Document serverStatus = mongoTemplate.getDb()
            .runCommand(new org.bson.Document("serverStatus", 1));
        
        org.bson.Document connections = serverStatus.get("connections", org.bson.Document.class);
        if (connections != null) {
            poolStats.put("current", connections.get("current"));
            poolStats.put("available", connections.get("available"));
            poolStats.put("totalCreated", connections.get("totalCreated"));
        }
        
        return ResponseEntity.ok(poolStats);
    }
}
