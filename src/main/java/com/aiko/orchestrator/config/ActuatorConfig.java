package com.aiko.orchestrator.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class ActuatorConfig {
    
    @Bean
    public HealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate) {
        return () -> {
            try {
                // Check MongoDB connection
                mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
                
                Map<String, Object> details = new HashMap<>();
                details.put("database", mongoTemplate.getDb().getName());
                details.put("status", "UP");
                details.put("timestamp", LocalDateTime.now());
                
                return Health.up()
                        .withDetails(details)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withException(e)
                        .build();
            }
        };
    }
    
    @Bean
    public InfoContributor applicationInfoContributor() {
        return builder -> {
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", "AI Knowledge Orchestrator");
            appInfo.put("description", "AI-powered knowledge orchestration system");
            appInfo.put("version", "1.0.0-SNAPSHOT");
            appInfo.put("team", "AIKO Team");
            
            Map<String, Object> features = new HashMap<>();
            features.put("authentication", "JWT");
            features.put("database", "MongoDB");
            features.put("monitoring", "Spring Actuator");
            features.put("documentation", "OpenAPI 3.0");
            
            builder.withDetail("application", appInfo);
            builder.withDetail("features", features);
            builder.withDetail("timestamp", LocalDateTime.now());
        };
    }
}
