package com.aiko.orchestrator.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
    
    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/ai_orchestrator}")
    private String mongoUri;
    
    @Value("${spring.data.mongodb.database:ai_orchestrator}")
    private String databaseName;
    
    @Override
    protected String getDatabaseName() {
        return databaseName;
    }
    
    @Override
    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings(settings -> settings
                .minSize(10)                          // Minimum connections in pool
                .maxSize(100)                         // Maximum connections in pool
                .maxWaitTime(120, TimeUnit.SECONDS)  // Max wait time for connection
                .maxConnectionLifeTime(0, TimeUnit.MILLISECONDS)  // Infinite lifetime
                .maxConnectionIdleTime(60000, TimeUnit.MILLISECONDS)  // 60 seconds idle
                .maintenanceFrequency(10, TimeUnit.SECONDS)  // Pool maintenance frequency
            )
            .applyToSocketSettings(settings -> settings
                .connectTimeout(10, TimeUnit.SECONDS)   // Connection timeout
                .readTimeout(0, TimeUnit.SECONDS)       // No read timeout
            )
            .applyToServerSettings(settings -> settings
                .heartbeatFrequency(10, TimeUnit.SECONDS)      // Heartbeat frequency
                .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)  // Min heartbeat
            )
            .applyToClusterSettings(settings -> settings
                .serverSelectionTimeout(30, TimeUnit.SECONDS)  // Server selection timeout
            )
            .applicationName("AI-Knowledge-Orchestrator")
            .build();
        
        log.info("MongoDB connection pool configured with min: 10, max: 100 connections");
        return MongoClients.create(mongoClientSettings);
    }
    
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
    
    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDbFactory());
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, new MongoMappingContext());
        converter.setTypeMapper(new DefaultMongoTypeMapper(null)); // Remove _class field
        
        MongoTemplate template = new MongoTemplate(mongoDbFactory(), converter);
        log.info("MongoTemplate configured for database: {}", databaseName);
        return template;
    }
}
