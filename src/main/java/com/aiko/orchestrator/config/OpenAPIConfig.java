package com.aiko.orchestrator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * Provides comprehensive API documentation with authentication details.
 * 
 * @author AIKO Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Configuration
public class OpenAPIConfig {
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${app.version:1.0.0}")
    private String appVersion;
    
    /**
     * Configures OpenAPI documentation with JWT security scheme.
     * 
     * @return OpenAPI configuration object
     */
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "Bearer Authentication";
        
        return new OpenAPI()
            .info(new Info()
                .title("AI Knowledge Orchestrator API")
                .version(appVersion)
                .description("""
                    ## AI Knowledge Orchestrator Backend API
                    
                    This API provides endpoints for an AI-powered knowledge orchestration system that combines:
                    - **Multi-agent AI orchestration** for intelligent query processing
                    - **Knowledge base management** with document storage and retrieval
                    - **MongoDB integration** for scalable data persistence
                    - **JWT authentication** for secure access control
                    
                    ### Key Features:
                    - 🤖 AI-powered query processing with multiple specialized agents
                    - 📚 Document management and knowledge base operations
                    - 🔍 Full-text search with MongoDB Atlas
                    - 🔐 Secure JWT-based authentication
                    - 📊 Performance monitoring and metrics
                    - 💾 Automated backup and recovery
                    
                    ### Authentication:
                    Most endpoints require JWT authentication. To access protected endpoints:
                    1. Register a new account or login
                    2. Copy the `access_token` from the response
                    3. Click 'Authorize' button and enter: `Bearer <your_token>`
                    
                    ### Rate Limiting:
                    - Default: 60 requests per minute
                    - Premium: 1000 requests per hour
                    """)
                .termsOfService("https://aiko.com/terms")
                .contact(new Contact()
                    .name("AIKO Development Team")
                    .email("support@aiko.com")
                    .url("https://github.com/aiko/ai-knowledge-orchestrator"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development Server"),
                new Server()
                    .url("https://api.aiko.com")
                    .description("Production Server"),
                new Server()
                    .url("https://staging-api.aiko.com")
                    .description("Staging Server")
            ))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter JWT Bearer token **_only_**")));
    }
}
