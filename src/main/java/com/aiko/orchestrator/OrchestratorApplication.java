package com.aiko.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the AI Knowledge Orchestrator.
 * 
 * <p>This Spring Boot application provides an intelligent knowledge orchestration system
 * that combines AI capabilities with document management and MongoDB persistence.</p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Multi-agent AI orchestration using LangChain</li>
 *   <li>MongoDB-based knowledge persistence</li>
 *   <li>JWT authentication and authorization</li>
 *   <li>RESTful API with OpenAPI documentation</li>
 *   <li>Asynchronous processing and caching</li>
 *   <li>Automated backups and monitoring</li>
 * </ul>
 * 
 * <h2>Configuration:</h2>
 * <p>The application uses Spring Boot auto-configuration with the following annotations:</p>
 * <ul>
 *   <li>{@code @EnableMongoRepositories} - Enables MongoDB repository support</li>
 *   <li>{@code @EnableMongoAuditing} - Enables MongoDB auditing for automatic timestamps</li>
 *   <li>{@code @EnableAsync} - Enables asynchronous method execution</li>
 *   <li>{@code @EnableScheduling} - Enables scheduled tasks (e.g., backups)</li>
 *   <li>{@code @EnableCaching} - Enables Spring Cache abstraction</li>
 * </ul>
 * 
 * @author AIKO Development Team
 * @version 1.0.0
 * @since 2024-01-01
 * @see <a href="https://spring.io/projects/spring-boot">Spring Boot Documentation</a>
 * @see <a href="https://www.mongodb.com/">MongoDB Documentation</a>
 */
@SpringBootApplication
@EnableMongoRepositories
@EnableMongoAuditing
@EnableAsync
@EnableScheduling
@EnableCaching
public class OrchestratorApplication {

    /**
     * Application entry point.
     * 
     * <p>Starts the Spring Boot application with embedded Tomcat server
     * and initializes all configured beans and components.</p>
     * 
     * <h3>JVM Arguments:</h3>
     * <pre>
     * -Xmx2g -Xms1g                    # Memory allocation
     * -Dspring.profiles.active=dev     # Active profile
     * -Dserver.port=8080               # Server port
     * </pre>
     * 
     * <h3>Environment Variables:</h3>
     * <pre>
     * MONGODB_URI=mongodb://localhost:27017/ai_orchestrator
     * JWT_SECRET=your-secret-key
     * </pre>
     * 
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
