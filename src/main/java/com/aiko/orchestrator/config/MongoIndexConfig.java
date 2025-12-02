package com.aiko.orchestrator.config;

import com.aiko.orchestrator.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {
    
    private final MongoTemplate mongoTemplate;
    
    @Bean
    public CommandLineRunner createIndexes() {
        return args -> {
            log.info("Starting MongoDB index creation...");
            
            createUserIndexes();
            createQueryIndexes();
            createKnowledgeIndexes();
            createResponseIndexes();
            createAuditLogIndexes();
            
            log.info("MongoDB index creation completed");
        };
    }
    
    private void createUserIndexes() {
        log.debug("Creating indexes for User collection");
        
        // Unique index on username
        mongoTemplate.indexOps(User.class)
            .ensureIndex(new Index()
                .on("username", Direction.ASC)
                .unique()
                .named("username_unique_idx"));
        
        // Unique index on email
        mongoTemplate.indexOps(User.class)
            .ensureIndex(new Index()
                .on("email", Direction.ASC)
                .unique()
                .named("email_unique_idx"));
        
        // Index for authentication queries
        mongoTemplate.indexOps(User.class)
            .ensureIndex(new Index()
                .on("username", Direction.ASC)
                .on("enabled", Direction.ASC)
                .named("username_enabled_idx"));
        
        // Index for refresh token lookups
        mongoTemplate.indexOps(User.class)
            .ensureIndex(new Index()
                .on("refreshToken", Direction.ASC)
                .sparse()
                .named("refresh_token_idx"));
        
        logIndexes("users");
    }
    
    private void createQueryIndexes() {
        log.debug("Creating indexes for Query collection");
        
        // Compound index for user queries sorted by date
        mongoTemplate.indexOps(Query.class)
            .ensureIndex(new Index()
                .on("userId", Direction.ASC)
                .on("createdAt", Direction.DESC)
                .named("user_date_compound_idx"));
        
        // Index for status-based queries
        mongoTemplate.indexOps(Query.class)
            .ensureIndex(new Index()
                .on("status", Direction.ASC)
                .on("createdAt", Direction.DESC)
                .named("status_date_idx"));
        
        // Index for query type filtering
        mongoTemplate.indexOps(Query.class)
            .ensureIndex(new Index()
                .on("queryType", Direction.ASC)
                .on("status", Direction.ASC)
                .named("type_status_idx"));
        
        // Text index for full-text search on query content
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
            .onField("queryText")
            .named("query_text_search_idx")
            .build();
        mongoTemplate.indexOps(Query.class).ensureIndex(textIndex);
        
        logIndexes("queries");
    }
    
    private void createKnowledgeIndexes() {
        log.debug("Creating indexes for Knowledge collection");
        
        // Compound index for type and status queries
        mongoTemplate.indexOps(Knowledge.class)
            .ensureIndex(new Index()
                .on("documentType", Direction.ASC)
                .on("processingStatus", Direction.ASC)
                .on("createdAt", Direction.DESC)
                .named("type_status_date_idx"));
        
        // Index for source-based queries
        mongoTemplate.indexOps(Knowledge.class)
            .ensureIndex(new Index()
                .on("source", Direction.ASC)
                .on("uploadedAt", Direction.DESC)
                .named("source_date_idx"));
        
        // Text index for full-text search
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
            .onField("title", 10F)      // Weight 10 for title
            .onField("content", 5F)      // Weight 5 for content
            .onField("summary", 7F)      // Weight 7 for summary
            .onField("tags")            // Default weight for tags
            .named("knowledge_text_search_idx")
            .build();
        mongoTemplate.indexOps(Knowledge.class).ensureIndex(textIndex);
        
        // Index for vector similarity search preparation
        mongoTemplate.indexOps(Knowledge.class)
            .ensureIndex(new Index()
                .on("embeddings", Direction.ASC)
                .sparse()
                .named("embeddings_idx"));
        
        logIndexes("knowledge");
    }
    
    private void createResponseIndexes() {
        log.debug("Creating indexes for Response collection");
        
        // Index for query lookup
        mongoTemplate.indexOps(Response.class)
            .ensureIndex(new Index()
                .on("queryId", Direction.ASC)
                .unique()
                .named("query_id_unique_idx"));
        
        // Index for agent type analysis
        mongoTemplate.indexOps(Response.class)
            .ensureIndex(new Index()
                .on("agentType", Direction.ASC)
                .on("status", Direction.ASC)
                .on("createdAt", Direction.DESC)
                .named("agent_status_date_idx"));
        
        // Index for confidence-based queries
        mongoTemplate.indexOps(Response.class)
            .ensureIndex(new Index()
                .on("confidenceScore", Direction.DESC)
                .on("status", Direction.ASC)
                .named("confidence_status_idx"));
        
        logIndexes("responses");
    }
    
    private void createAuditLogIndexes() {
        log.debug("Creating indexes for AuditLog collection");
        
        // Compound index for user activity queries
        mongoTemplate.indexOps(AuditLog.class)
            .ensureIndex(new Index()
                .on("userId", Direction.ASC)
                .on("timestamp", Direction.DESC)
                .named("user_activity_idx"));
        
        // Index for entity-based audit queries
        mongoTemplate.indexOps(AuditLog.class)
            .ensureIndex(new Index()
                .on("entityId", Direction.ASC)
                .on("entityType", Direction.ASC)
                .on("timestamp", Direction.DESC)
                .named("entity_audit_idx"));
        
        // Index for action type filtering
        mongoTemplate.indexOps(AuditLog.class)
            .ensureIndex(new Index()
                .on("action", Direction.ASC)
                .on("timestamp", Direction.DESC)
                .named("action_time_idx"));
        
        // TTL index to automatically delete old audit logs after 90 days
        mongoTemplate.indexOps(AuditLog.class)
            .ensureIndex(new Index()
                .on("timestamp", Direction.ASC)
                .expire(7776000) // 90 days in seconds
                .named("audit_ttl_idx"));
        
        logIndexes("auditLogs");
    }
    
    private void logIndexes(String collection) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(collection).getIndexInfo();
        log.info("Indexes for collection '{}': {}", collection, 
            indexes.stream().map(IndexInfo::getName).toList());
    }
}
