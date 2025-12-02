package com.aiko.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {
    
    private final MongoTemplate mongoTemplate;
    
    @GetMapping
    @Operation(summary = "Basic health check")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "AI Knowledge Orchestrator");
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/detailed")
    @Operation(summary = "Detailed health check with dependencies")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        
        // Check MongoDB
        Map<String, Object> mongodb = new HashMap<>();
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            mongodb.put("status", "UP");
            mongodb.put("database", mongoTemplate.getDb().getName());
        } catch (Exception e) {
            mongodb.put("status", "DOWN");
            mongodb.put("error", e.getMessage());
            health.put("status", "DOWN");
        }
        health.put("mongodb", mongodb);
        
        // Application info
        Map<String, Object> application = new HashMap<>();
        application.put("name", "AI Knowledge Orchestrator");
        application.put("version", "1.0.0-SNAPSHOT");
        application.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        health.put("application", application);
        
        // JVM info
        Map<String, Object> jvm = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        jvm.put("maxMemory", runtime.maxMemory() / 1024 / 1024 + " MB");
        jvm.put("totalMemory", runtime.totalMemory() / 1024 / 1024 + " MB");
        jvm.put("freeMemory", runtime.freeMemory() / 1024 / 1024 + " MB");
        jvm.put("processors", runtime.availableProcessors());
        health.put("jvm", jvm);
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/ready")
    @Operation(summary = "Readiness probe for Kubernetes")
    public ResponseEntity<Map<String, String>> ready() {
        // Check if the application is ready to serve traffic
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            Map<String, String> response = new HashMap<>();
            response.put("status", "READY");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Readiness check failed: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "NOT_READY");
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }
    
    @GetMapping("/live")
    @Operation(summary = "Liveness probe for Kubernetes")
    public ResponseEntity<Map<String, String>> live() {
        // Basic liveness check
        Map<String, String> response = new HashMap<>();
        response.put("status", "ALIVE");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
