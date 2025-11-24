package com.aiko.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration class to enable asynchronous method execution in Spring Boot.
 * This allows methods annotated with @Async to run in separate threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // @EnableAsync annotation enables Spring's asynchronous method execution capability
    // The actual thread pool configuration is handled in TaskExecutorConfig
}
