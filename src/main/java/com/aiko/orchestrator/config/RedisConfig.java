package com.aiko.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Redis configuration for caching and session management
 * Configures Redis connection, serialization, and templates
 */
@Slf4j
@Configuration
public class RedisConfig {
    
    @Value("${spring.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.redis.database:0}")
    private int redisDatabase;
    
    @Value("${spring.redis.timeout:2000}")
    private long redisTimeout;
    
    @Value("${spring.redis.lettuce.pool.max-active:8}")
    private int maxActive;
    
    @Value("${spring.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;
    
    @Value("${spring.redis.lettuce.pool.min-idle:0}")
    private int minIdle;
    
    /**
     * Configure Redis connection factory with Lettuce client
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        
        // Redis standalone configuration
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisHost);
        redisStandaloneConfiguration.setPort(redisPort);
        redisStandaloneConfiguration.setDatabase(redisDatabase);
        
        // Set password if provided
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisStandaloneConfiguration.setPassword(redisPassword);
        }
        
        // Configure Lettuce client options
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(redisTimeout)))
                .build();
        
        // Lettuce client configuration
        LettuceClientConfiguration lettuceClientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofMillis(redisTimeout))
                .build();
        
        // Create connection factory
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(
                redisStandaloneConfiguration, lettuceClientConfiguration);
        
        log.info("Redis connection factory configured successfully");
        
        return lettuceConnectionFactory;
    }
    
    /**
     * Configure RedisTemplate for generic Redis operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // Use Jackson JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        // Enable transaction support
        template.setEnableTransactionSupport(true);
        
        template.afterPropertiesSet();
        
        log.info("RedisTemplate configured with JSON serialization");
        
        return template;
    }
    
    /**
     * Configure RedisTemplate specifically for String operations
     */
    // @Bean
    // public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    //     RedisTemplate<String, String> template = new RedisTemplate<>();
    //     template.setConnectionFactory(connectionFactory);
        
    //     StringRedisSerializer serializer = new StringRedisSerializer();
    //     template.setKeySerializer(serializer);
    //     template.setValueSerializer(serializer);
    //     template.setHashKeySerializer(serializer);
    //     template.setHashValueSerializer(serializer);
        
    //     template.afterPropertiesSet();
        
    //     return template;
    // }
}
