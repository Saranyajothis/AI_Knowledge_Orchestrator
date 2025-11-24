package com.aiko.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when rate limit is exceeded
 * Returns HTTP 429 (Too Many Requests) by default
 */
@ResponseStatus(value = HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {
    
    private final String clientIdentifier;
    private final long retryAfterSeconds;
    private final String rateLimitKey;
    
    public RateLimitExceededException(String message) {
        super(message);
        this.clientIdentifier = null;
        this.retryAfterSeconds = 60; // Default retry after 60 seconds
        this.rateLimitKey = null;
    }
    
    public RateLimitExceededException(String message, String clientIdentifier, long retryAfterSeconds) {
        super(message);
        this.clientIdentifier = clientIdentifier;
        this.retryAfterSeconds = retryAfterSeconds;
        this.rateLimitKey = null;
    }
    
    public RateLimitExceededException(String message, String clientIdentifier, long retryAfterSeconds, String rateLimitKey) {
        super(message);
        this.clientIdentifier = clientIdentifier;
        this.retryAfterSeconds = retryAfterSeconds;
        this.rateLimitKey = rateLimitKey;
    }
    
    public String getClientIdentifier() {
        return clientIdentifier;
    }
    
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    public String getRateLimitKey() {
        return rateLimitKey;
    }
}
