# Request/Response Logging Configuration

## Overview
This document describes the request/response logging feature implementation for the AI Knowledge Orchestrator project.

## ✅ Implementation Complete

### Components Created:

#### 1. **RequestResponseLoggingInterceptor.java** (Interceptor Approach)
- Implements `HandlerInterceptor` interface
- Logs all incoming requests and outgoing responses
- Stores request/response details in MongoDB via AuditLog
- Features:
  - Request ID generation and tracking
  - Client IP detection (handles proxy headers)
  - Processing time calculation
  - Error logging with stack traces
  - Sensitive header filtering (auth tokens, cookies)
  - Slow request detection (> 5 seconds)

#### 2. **LoggingFilter.java** (Filter Approach - Alternative)
- Implements `Filter` interface  
- Provides access to request/response bodies
- Uses `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper`
- Additional features:
  - Request/response body capture
  - Body truncation for large payloads (> 5000 chars)
  - Better for debugging API payloads

**Note:** Use EITHER the Interceptor OR the Filter, not both!

#### 3. **WebMvcConfig.java**
- Registers the interceptor with Spring MVC
- Configures URL patterns:
  - Includes: `/api/**`
  - Excludes: health checks, actuator, swagger, docs

#### 4. **Enhanced AuditLog.java Model**
Added fields for HTTP request/response logging:
- `requestId` - Unique identifier for each request
- `httpMethod` - GET, POST, PUT, DELETE, etc.
- `requestUri` - Request path
- `queryString` - Query parameters
- `clientIp` - Client IP address
- `requestHeaders` / `responseHeaders` - HTTP headers
- `requestBody` / `responseBody` - Payload content
- `responseStatus` - HTTP status code
- `processingTimeMs` - Request processing time
- `requestTime` / `responseTime` - Timestamps
- `errorMessage` / `errorStackTrace` - Error details

#### 5. **Enhanced AuditLogRepository.java**
Added query methods:
- `findByRequestId()` - Find logs by request ID
- `findByResponseStatusGreaterThanEqual()` - Find failed requests
- `findByProcessingTimeMsGreaterThan()` - Find slow requests
- `findByClientIp()` - Track requests from specific IP
- `findFailedRequestsInTimeRange()` - Failed requests in time window
- `findSlowRequests()` - Performance analysis
- `getRequestStatsByMethod()` - Aggregated statistics

#### 6. **AuditLogController.java**
REST API for viewing and analyzing logs:
- `GET /api/v1/audit-logs` - View all logs with pagination
- `GET /api/v1/audit-logs/user/{userId}` - User-specific logs
- `GET /api/v1/audit-logs/request/{requestId}` - Logs by request ID
- `GET /api/v1/audit-logs/failed` - Failed requests (4xx, 5xx)
- `GET /api/v1/audit-logs/slow` - Slow requests
- `GET /api/v1/audit-logs/errors` - Requests with errors
- `GET /api/v1/audit-logs/stats` - Request statistics
- `GET /api/v1/audit-logs/dashboard` - Dashboard metrics
- `DELETE /api/v1/audit-logs/cleanup` - Clean old logs

## Configuration

### application.yml Settings
```yaml
logging:
  level:
    com.aiko.orchestrator.interceptor: DEBUG
    com.aiko.orchestrator.filter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/orchestrator.log
    max-size: 10MB
    max-history: 30

# Custom logging configuration
app:
  logging:
    enable-request-logging: true
    enable-response-logging: true
    log-request-body: true
    log-response-body: true
    max-body-size: 5000  # Max characters to log for body
    slow-request-threshold: 5000  # ms
    sensitive-headers:
      - Authorization
      - Cookie
      - X-API-Key
      - Token
```

## Usage Examples

### 1. View Recent Requests
```bash
# Get latest audit logs
curl http://localhost:8080/api/v1/audit-logs?page=0&size=10

# Get failed requests
curl http://localhost:8080/api/v1/audit-logs/failed

# Get slow requests (>5 seconds)
curl http://localhost:8080/api/v1/audit-logs/slow?thresholdMs=5000
```

### 2. Track Specific Request
```bash
# Get logs for a specific request ID (from X-Request-Id header)
curl http://localhost:8080/api/v1/audit-logs/request/abc-123-def
```

### 3. View Statistics
```bash
# Get request statistics for last 24 hours
curl http://localhost:8080/api/v1/audit-logs/stats

# Get dashboard metrics
curl http://localhost:8080/api/v1/audit-logs/dashboard
```

### 4. Monitor User Activity
```bash
# Get all requests from a specific user
curl http://localhost:8080/api/v1/audit-logs/user/user123

# Get requests from a specific IP
curl http://localhost:8080/api/v1/audit-logs/ip/192.168.1.100
```

## Log Output Examples

### Successful Request Log
```
2024-11-21 10:30:45 - ==> REQUEST [POST] /api/v1/queries?priority=high HTTP/1.1 from 192.168.1.100 | Request-ID: f47ac10b-58cc-4372-a567-0e02b2c3d479
2024-11-21 10:30:46 - <== RESPONSE [POST] /api/v1/queries 201 | Created | 1250ms | Request-ID: f47ac10b-58cc-4372-a567-0e02b2c3d479
```

### Failed Request Log
```
2024-11-21 10:31:15 - ==> REQUEST [GET] /api/v1/knowledge/invalid-id HTTP/1.1 from 192.168.1.100 | Request-ID: 6ba7b810-9dad-11d1-80b4-00c04fd430c8
2024-11-21 10:31:15 - <== RESPONSE [GET] /api/v1/knowledge/invalid-id 404 | Not Found | 45ms | Request-ID: 6ba7b810-9dad-11d1-80b4-00c04fd430c8
```

### Slow Request Warning
```
2024-11-21 10:32:00 - SLOW REQUEST: [POST] /api/v1/knowledge/upload took 8750ms | Request-ID: 550e8400-e29b-41d4-a716-446655440000
```

## Benefits

1. **Complete Request Tracking**: Every API request is logged with a unique ID
2. **Performance Monitoring**: Identify slow endpoints and bottlenecks
3. **Error Tracking**: Capture and analyze errors with stack traces
4. **Security Auditing**: Track who accessed what and when
5. **Debugging**: Request/response bodies help debug issues
6. **Analytics**: Built-in statistics and dashboard metrics
7. **Compliance**: Audit trail for regulatory requirements

## Choosing Between Interceptor and Filter

### Use the Interceptor when:
- You only need to log metadata (headers, status, timing)
- You want better Spring MVC integration
- You're using `@ControllerAdvice` for error handling

### Use the Filter when:
- You need to log request/response bodies
- You want to modify request/response content
- You need to work at a lower level (before Spring MVC)

## Performance Considerations

1. **Async Logging**: Logs are written to MongoDB asynchronously to minimize impact
2. **Body Size Limits**: Large request/response bodies are truncated
3. **Selective Logging**: Health checks and static resources are excluded
4. **Indexed Fields**: MongoDB indexes on frequently queried fields

## Security Considerations

1. **Sensitive Data**: Authorization headers and tokens are filtered out
2. **PII Protection**: Consider what data is logged in production
3. **Log Retention**: Implement regular cleanup of old logs
4. **Access Control**: Restrict access to audit log endpoints

## Next Steps

To enable logging in your application:

1. **Choose approach**: Decide between Interceptor (default) or Filter
2. **If using Filter**: Comment out `@Component` in Interceptor or vice versa
3. **Start application**: Logs will automatically be captured
4. **View logs**: Use the AuditLogController endpoints
5. **Monitor**: Check dashboard for real-time metrics

## Testing

```bash
# Make any API request
curl -X POST http://localhost:8080/api/v1/queries \
  -H "Content-Type: application/json" \
  -H "X-User-Id: test-user" \
  -d '{"queryText": "Test query", "queryType": "GENERAL"}'

# Check the response headers for Request-ID
# Response will include: X-Request-Id: <unique-id>

# View the audit log for this request
curl http://localhost:8080/api/v1/audit-logs/request/<unique-id>
```

## Troubleshooting

If logs are not appearing:
1. Check if the interceptor/filter is registered in WebMvcConfig
2. Verify MongoDB connection
3. Check log levels in application.yml
4. Ensure the URL pattern matches your endpoints
5. Look for errors in application logs

## Summary

The Request/Response Logging feature is now fully implemented and provides comprehensive logging of all API activity. It captures detailed information about each request, stores it in MongoDB, and provides rich analytics through the AuditLogController endpoints.
