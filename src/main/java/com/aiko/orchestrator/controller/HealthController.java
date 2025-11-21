package com.aiko.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health Check", description = "System health monitoring")
public class HealthController {
    
    private final MongoTemplate mongoTemplate;
    
    @GetMapping
    @Operation(summary = "Check system health status")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        // Check MongoDB connection
        try {
            mongoTemplate.getDb().getName();
            health.put("mongodb", "Connected");
        } catch (Exception e) {
            health.put("mongodb", "Disconnected");
            health.put("status", "DOWN");
        }
        
        return health;
    }
}