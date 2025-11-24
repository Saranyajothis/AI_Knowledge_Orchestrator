package com.aiko.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableMongoRepositories(basePackages = "com.aiko.orchestrator.repository")
@EnableMongoAuditing
@EnableAsync
public class MongoConfig {
    // MongoDB configuration is handled by Spring Boot auto-configuration
    // Additional custom configuration can be added here if needed
}