# API Usage Examples

This document provides comprehensive examples for using the AI Knowledge Orchestrator API.

## Table of Contents

- [Authentication](#authentication)
- [Query Management](#query-management)
- [Knowledge Base](#knowledge-base)
- [Admin Operations](#admin-operations)
- [Error Handling](#error-handling)
- [Pagination Examples](#pagination-examples)
- [WebSocket Events](#websocket-events)

## Authentication

### Register a New User

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice_smith",
    "email": "alice@example.com",
    "password": "SecurePass123!",
    "firstName": "Alice",
    "lastName": "Smith"
  }'
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZV9zbWl0aCIsImlhdCI6MTcwNTMyMDAwMCwiZXhwIjoxNzA1NDA2NDAwfQ.signature",
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZV9zbWl0aCIsImlhdCI6MTcwNTMyMDAwMCwiZXhwIjoxNzA1OTI0ODAwfQ.signature",
  "token_type": "Bearer",
  "expires_in": 86400,
  "username": "alice_smith",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Smith",
  "roles": ["USER"],
  "user_id": "507f1f77bcf86cd799439011"
}
```

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "alice@example.com",
    "password": "SecurePass123!"
  }'
```

### Refresh Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Refresh-Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZV9zbWl0aCIsImlhdCI6MTcwNTMyMDAwMCwiZXhwIjoxNzA1OTI0ODAwfQ.signature"
```

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Refresh-Token: YOUR_REFRESH_TOKEN"
```

## Query Management

### Submit a Query

```bash
curl -X POST http://localhost:8080/api/v1/queries \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryText": "What are the best practices for implementing microservices architecture?",
    "queryType": "RESEARCH",
    "context": {
      "domain": "software-architecture",
      "priority": "high",
      "maxResponseTime": 30
    },
    "metadata": {
      "project": "system-redesign",
      "team": "backend"
    }
  }'
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "userId": "507f1f77bcf86cd799439000",
  "queryText": "What are the best practices for implementing microservices architecture?",
  "queryType": "RESEARCH",
  "status": "PENDING",
  "context": {
    "domain": "software-architecture",
    "priority": "high",
    "maxResponseTime": 30
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### Get Query Status

```bash
curl -X GET http://localhost:8080/api/v1/queries/507f1f77bcf86cd799439011 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "queryText": "What are the best practices for implementing microservices architecture?",
  "status": "COMPLETED",
  "response": {
    "content": "Here are the best practices for implementing microservices architecture:\n\n1. **Service Independence**: Each service should be independently deployable...\n2. **API Gateway**: Implement an API gateway for routing...\n3. **Service Discovery**: Use service discovery mechanisms...",
    "confidence": 0.92,
    "processingTime": 2340,
    "agentType": "RESEARCH",
    "sources": [
      {
        "id": "doc_001",
        "title": "Microservices Patterns",
        "relevance": 0.95
      },
      {
        "id": "doc_002",
        "title": "Building Microservices",
        "relevance": 0.88
      }
    ],
    "metadata": {
      "tokensUsed": 1250,
      "model": "gpt-4",
      "temperature": 0.7
    }
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:02Z"
}
```

### Get Query History with Pagination

```bash
# Using cursor-based pagination
curl -X GET "http://localhost:8080/api/v1/queries/history?cursor=507f1f77bcf86cd799439000&limit=10" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

```bash
# Using offset-based pagination
curl -X GET "http://localhost:8080/api/v1/queries/history?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### Search Queries

```bash
curl -X GET "http://localhost:8080/api/v1/queries/search?q=microservices&status=COMPLETED&from=2024-01-01&to=2024-01-31" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Knowledge Base

### Upload Document

```bash
curl -X POST http://localhost:8080/api/v1/knowledge/upload \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -F "file=@/path/to/document.pdf" \
  -F 'metadata={"category":"architecture","tags":["microservices","patterns","best-practices"],"confidential":false}'
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439012",
  "filename": "document.pdf",
  "originalFilename": "Microservices_Best_Practices_2024.pdf",
  "contentType": "application/pdf",
  "size": 2457600,
  "checksum": "d41d8cd98f00b204e9800998ecf8427e",
  "processingStatus": "PROCESSING",
  "metadata": {
    "category": "architecture",
    "tags": ["microservices", "patterns", "best-practices"],
    "confidential": false
  },
  "uploadedBy": "alice_smith",
  "uploadedAt": "2024-01-15T11:00:00Z"
}
```

### Search Knowledge Base

```bash
curl -X POST http://localhost:8080/api/v1/knowledge/search \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "microservices communication patterns",
    "filters": {
      "category": "architecture",
      "tags": ["patterns"],
      "dateRange": {
        "from": "2024-01-01",
        "to": "2024-12-31"
      }
    },
    "limit": 10,
    "includeContent": false
  }'
```

**Response:**
```json
{
  "results": [
    {
      "id": "507f1f77bcf86cd799439012",
      "title": "Microservices Communication Patterns",
      "summary": "This document covers various communication patterns including synchronous REST, asynchronous messaging...",
      "relevanceScore": 0.94,
      "metadata": {
        "category": "architecture",
        "tags": ["patterns", "communication"],
        "author": "John Doe",
        "pageCount": 45
      },
      "highlights": [
        "...implementing <em>microservices communication patterns</em> effectively requires...",
        "...the <em>pattern</em> choice depends on your specific <em>microservices</em> architecture..."
      ]
    }
  ],
  "totalResults": 23,
  "executionTime": 125
}
```

### Get Document Content

```bash
curl -X GET http://localhost:8080/api/v1/knowledge/507f1f77bcf86cd799439012 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### Delete Document

```bash
curl -X DELETE http://localhost:8080/api/v1/knowledge/507f1f77bcf86cd799439012 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Admin Operations

### Get MongoDB Statistics

```bash
curl -X GET http://localhost:8080/api/v1/admin/mongodb/stats \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

**Response:**
```json
{
  "database": "ai_orchestrator",
  "collections": 5,
  "dataSize": 52428800,
  "storageSize": 61440000,
  "indexes": 12,
  "indexSize": 2097152,
  "totalDocuments": 15234,
  "avgDocumentSize": 3440
}
```

### Analyze Query Performance

```bash
curl -X POST "http://localhost:8080/api/v1/admin/mongodb/analyze-query?collection=queries" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "507f1f77bcf86cd799439000",
    "status": "COMPLETED",
    "createdAt": {"$gte": "2024-01-01"}
  }'
```

**Response:**
```json
{
  "executionTimeMs": 45,
  "totalDocsExamined": 1250,
  "totalKeysExamined": 1250,
  "nReturned": 1250,
  "indexUsed": true,
  "stage": "IXSCAN",
  "efficiencyScore": 1.0,
  "optimizationTips": []
}
```

### Trigger Manual Backup

```bash
curl -X POST "http://localhost:8080/api/v1/admin/mongodb/backup?backupType=manual" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

**Response:**
```json
{
  "success": true,
  "backupPath": "./backup/mongodb/manual_20240115_110000",
  "backupSize": 52428800,
  "timestamp": "20240115_110000",
  "duration": 3450,
  "collections": ["users", "queries", "responses", "knowledge", "auditLogs"]
}
```

### Get Slow Queries

```bash
curl -X GET "http://localhost:8080/api/v1/admin/mongodb/slow-queries?thresholdMs=100" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

## Error Handling

### Error Response Format

All errors follow this format:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/queries",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "validationErrors": {
    "queryText": "Query text is required",
    "queryType": "Query type must be one of: RESEARCH, CODE, DECISION"
  }
}
```

### Common Error Codes

- **400 Bad Request**: Invalid input data
- **401 Unauthorized**: Missing or invalid authentication
- **403 Forbidden**: Insufficient permissions
- **404 Not Found**: Resource not found
- **409 Conflict**: Resource already exists
- **429 Too Many Requests**: Rate limit exceeded
- **500 Internal Server Error**: Server error

## Pagination Examples

### Cursor-Based Pagination (Recommended for Large Datasets)

```javascript
// JavaScript example
async function getAllQueries(token) {
  let allQueries = [];
  let cursor = null;
  
  while (true) {
    const url = cursor 
      ? `http://localhost:8080/api/v1/queries/history?cursor=${cursor}&limit=100`
      : 'http://localhost:8080/api/v1/queries/history?limit=100';
    
    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    const data = await response.json();
    allQueries = allQueries.concat(data.content);
    
    if (!data.hasNext) {
      break;
    }
    
    cursor = data.nextCursor;
  }
  
  return allQueries;
}
```

### Offset-Based Pagination

```python
# Python example
import requests

def get_all_queries(token):
    all_queries = []
    page = 0
    size = 20
    
    while True:
        response = requests.get(
            f'http://localhost:8080/api/v1/queries/history',
            params={'page': page, 'size': size, 'sort': 'createdAt,desc'},
            headers={'Authorization': f'Bearer {token}'}
        )
        
        data = response.json()
        all_queries.extend(data['content'])
        
        if data['last']:
            break
            
        page += 1
    
    return all_queries
```

## WebSocket Events (Future Feature)

### Connect to WebSocket

```javascript
const socket = new WebSocket('ws://localhost:8080/ws');

socket.onopen = function(event) {
  // Send authentication
  socket.send(JSON.stringify({
    type: 'auth',
    token: 'YOUR_ACCESS_TOKEN'
  }));
};

socket.onmessage = function(event) {
  const message = JSON.parse(event.data);
  
  switch(message.type) {
    case 'query-update':
      console.log('Query status updated:', message.data);
      break;
    case 'processing-complete':
      console.log('Processing complete:', message.data);
      break;
    case 'error':
      console.error('Error:', message.error);
      break;
  }
};

// Subscribe to query updates
socket.send(JSON.stringify({
  type: 'subscribe',
  queryId: '507f1f77bcf86cd799439011'
}));
```

## Rate Limiting

The API implements rate limiting with the following defaults:

- **Anonymous**: 10 requests per minute
- **Authenticated**: 60 requests per minute
- **Premium**: 1000 requests per hour

Rate limit headers in response:
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1705320600
```

## SDK Examples

### Java Client

```java
// Using RestTemplate
RestTemplate restTemplate = new RestTemplate();

HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(accessToken);
headers.setContentType(MediaType.APPLICATION_JSON);

QueryRequest request = QueryRequest.builder()
    .queryText("How to implement caching in Spring Boot?")
    .queryType("RESEARCH")
    .build();

HttpEntity<QueryRequest> entity = new HttpEntity<>(request, headers);

ResponseEntity<QueryResponse> response = restTemplate.exchange(
    "http://localhost:8080/api/v1/queries",
    HttpMethod.POST,
    entity,
    QueryResponse.class
);
```

### Python Client

```python
import requests
from typing import Dict, Any

class AIKnowledgeClient:
    def __init__(self, base_url: str, access_token: str):
        self.base_url = base_url
        self.headers = {
            'Authorization': f'Bearer {access_token}',
            'Content-Type': 'application/json'
        }
    
    def submit_query(self, query_text: str, query_type: str = 'RESEARCH') -> Dict[str, Any]:
        response = requests.post(
            f'{self.base_url}/api/v1/queries',
            json={
                'queryText': query_text,
                'queryType': query_type
            },
            headers=self.headers
        )
        response.raise_for_status()
        return response.json()
    
    def get_query_result(self, query_id: str) -> Dict[str, Any]:
        response = requests.get(
            f'{self.base_url}/api/v1/queries/{query_id}',
            headers=self.headers
        )
        response.raise_for_status()
        return response.json()

# Usage
client = AIKnowledgeClient('http://localhost:8080', 'your_access_token')
query = client.submit_query('What is dependency injection?')
result = client.get_query_result(query['id'])
```

## Testing with Postman

Import this collection into Postman:

```json
{
  "info": {
    "name": "AI Knowledge Orchestrator",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8080"
    },
    {
      "key": "accessToken",
      "value": ""
    }
  ],
  "item": [
    {
      "name": "Authentication",
      "item": [
        {
          "name": "Register",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"username\": \"testuser\",\n  \"email\": \"test@example.com\",\n  \"password\": \"Test123!\",\n  \"firstName\": \"Test\",\n  \"lastName\": \"User\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/v1/auth/register",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "auth", "register"]
            }
          }
        }
      ]
    }
  ]
}
```

## Troubleshooting

### Common Issues

1. **401 Unauthorized**
   - Check if token is expired
   - Verify token format: `Bearer <token>`
   - Ensure token has required permissions

2. **Connection Refused**
   - Verify MongoDB is running
   - Check application is running on correct port
   - Verify firewall settings

3. **Slow Queries**
   - Check if indexes are created
   - Monitor connection pool usage
   - Review query complexity

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.aiko.orchestrator: DEBUG
    org.springframework.data.mongodb: DEBUG
```

View correlation IDs in logs for request tracking:
```
2024-01-15 10:30:00 [550e8400-e29b-41d4-a716-446655440000] DEBUG QueryService - Processing query: 507f1f77bcf86cd799439011
```
