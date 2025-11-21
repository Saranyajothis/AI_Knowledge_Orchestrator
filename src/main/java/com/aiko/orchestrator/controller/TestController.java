// package com.aiko.orchestrator.controller;

// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.RestController;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.mongodb.core.MongoTemplate;
// import java.util.HashMap;
// import java.util.Map;

// @RestController
// public class TestController {
    
//     @Autowired
//     private MongoTemplate mongoTemplate;
    
//     @GetMapping("/")
//     public Map<String, String> home() {
//         Map<String, String> response = new HashMap<>();
//         response.put("message", "AI Knowledge Orchestrator is running!");
//         response.put("status", "OK");
//         response.put("database", "Connected to MongoDB");
        
//         // This will create the database if it doesn't exist
//         mongoTemplate.createCollection("test");
        
//         return response;
//     }
    
//     @GetMapping("/api/health")
//     public Map<String, String> health() {
//         Map<String, String> response = new HashMap<>();
//         response.put("status", "UP");
//         response.put("mongodb", mongoTemplate.getDb().getName());
//         return response;
//     }
// }