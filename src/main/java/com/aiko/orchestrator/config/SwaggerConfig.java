package com.aiko.orchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Knowledge Orchestrator API")
                        .version("1.0.0")
                        .description("REST API for AI Knowledge Orchestrator System")
                        .contact(new Contact()
                                .name("AI Knowledge Team")
                                .email("support@aiknowledge.com")));
    }
}