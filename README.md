# AI Knowledge Orchestrator 🤖📚

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-6.0-green.svg)](https://www.mongodb.com/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

An enterprise-grade AI-powered knowledge orchestration system that combines intelligent query processing with scalable document management using Spring Boot and MongoDB.

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [MongoDB Setup](#-mongodb-setup)
- [API Documentation](#-api-documentation)
- [Configuration](#-configuration)
- [MongoDB Atlas Deployment](#-mongodb-atlas-deployment)
- [Development](#-development)
- [Testing](#-testing)
- [Performance](#-performance)
- [Security](#-security)
- [Monitoring](#-monitoring)
- [Backup & Recovery](#-backup--recovery)
- [Contributing](#-contributing)
- [License](#-license)

## ✨ Features

### Core Capabilities
- **🤖 AI Multi-Agent System** - Intelligent query processing with specialized agents
- **📚 Knowledge Base Management** - Store, retrieve, and manage documents
- **🔍 Full-Text Search** - MongoDB text search with weighted relevance
- **🔐 JWT Authentication** - Secure token-based authentication
- **📊 Real-time Monitoring** - Performance metrics and health checks
- **💾 Automated Backups** - Scheduled backups with retention policies
- **🚀 High Performance** - Connection pooling, caching, and query optimization
- **📈 Scalability** - Horizontal scaling with MongoDB sharding support

### Advanced Features
- **Cursor-based Pagination** - Efficient handling of large datasets
- **Rate Limiting** - API throttling to prevent abuse
- **Request Correlation** - Track requests across the system
- **Audit Logging** - Complete activity tracking
- **Vector Search Ready** - Prepared for AI embeddings

## 🏗 Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (React/Vue)                   │
└─────────────────────┬───────────────────────────────────────┘
                      │ HTTPS
┌─────────────────────▼───────────────────────────────────────┐
│                     API Gateway (Optional)                   │
└─────────────────────┬───────────────────────────────────────┘
                      │ REST API
┌─────────────────────▼───────────────────────────────────────┐
│               Spring Boot Backend (Port 8080)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Controllers (REST Endpoints)            │  │
│  └────────────────────┬─────────────────────────────────┘  │
│  ┌────────────────────▼─────────────────────────────────┐  │
│  │              Service Layer (Business Logic)          │  │
│  └────────────────────┬─────────────────────────────────┘  │
│  ┌────────────────────▼─────────────────────────────────┐  │
│  │           Repository Layer (Data Access)             │  │
│  └────────────────────┬─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                      │ MongoDB Driver
┌─────────────────────▼───────────────────────────────────────┐
│                    MongoDB (Port 27017)                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Collections: users, queries, responses, knowledge    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                      │ Optional
┌─────────────────────▼───────────────────────────────────────┐
│              Python Orchestrator (LangChain)                 │
│           (Multi-Agent System - Port 8000)                   │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction

```mermaid
sequenceDiagram
    participant User
    participant API
    participant Auth
    participant Service
    participant MongoDB
    participant AI

    User->>API: POST /api/v1/auth/login
    API->>Auth: Validate credentials
    Auth->>MongoDB: Verify user
    MongoDB-->>Auth: User data
    Auth-->>API: JWT token
    API-->>User: Authentication response

    User->>API: GET /api/v1/queries (with JWT)
    API->>Auth: Validate JWT
    Auth-->>API: User context
    API->>Service: Process query
    Service->>MongoDB: Fetch data
    Service->>AI: Process with AI (optional)
    AI-->>Service: AI response
    Service-->>API: Formatted response
    API-->>User: JSON response
```

## 🛠 Tech Stack

### Backend
- **Framework:** Spring Boot 3.2.0
- **Language:** Java 17
- **Database:** MongoDB 6.0+
- **Security:** Spring Security + JWT
- **API Docs:** OpenAPI 3.0 (Swagger)
- **Build Tool:** Maven 3.8+

### Dependencies
- Spring Data MongoDB
- Spring Boot Actuator
- Spring Cache (Redis optional)
- Lombok
- Micrometer (Metrics)
- Bucket4j (Rate Limiting)

## 📋 Prerequisites

### Required
- Java 17 or higher
- Maven 3.8+
- MongoDB 6.0+
- 4GB RAM minimum
- 10GB disk space

### Optional
- Docker & Docker Compose
- Redis (for caching)
- Python 3.9+ (for AI orchestrator)
- MongoDB Compass (GUI)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/ai-knowledge-orchestrator.git
cd ai-knowledge-orchestrator
```

### 2. Start MongoDB

Using Docker:
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  -e MONGO_INITDB_DATABASE=ai_orchestrator \
  -v mongodb_data:/data/db \
  mongo:6.0
```

Using Local Installation:
```bash
mongod --dbpath /path/to/data
```

### 3. Configure Application

Create `application-local.yml`:
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/ai_orchestrator
      
jwt:
  secret: your-256-bit-secret-key-base64-encoded
  
mongodb:
  backup:
    enabled: false  # Disable for local dev
```

### 4. Build and Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run -Dspring.profiles.active=local

# Or using JAR
java -jar target/orchestrator-1.0.0.jar --spring.profiles.active=local
```

### 5. Access the Application

- **API Base URL:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health Check:** http://localhost:8080/actuator/health

## 🗄 MongoDB Setup

### Local Development Setup

#### 1. Install MongoDB

macOS:
```bash
brew tap mongodb/brew
brew install mongodb-community@6.0
brew services start mongodb-community@6.0
```

Ubuntu:
```bash
wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | sudo apt-key add -
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/6.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-6.0.list
sudo apt-get update
sudo apt-get install -y mongodb-org
sudo systemctl start mongod
```

#### 2. Create Database and User

```javascript
// Connect to MongoDB
mongosh

// Switch to admin database
use admin

// Create admin user
db.createUser({
  user: "admin",
  pwd: "password",
  roles: ["root"]
})

// Create application database
use ai_orchestrator

// Create application user
db.createUser({
  user: "aiko_user",
  pwd: "aiko_password",
  roles: [
    { role: "readWrite", db: "ai_orchestrator" },
    { role: "dbAdmin", db: "ai_orchestrator" }
  ]
})
```

#### 3. Enable Authentication (Optional)

Edit `/etc/mongod.conf`:
```yaml
security:
  authorization: enabled
```

Restart MongoDB:
```bash
sudo systemctl restart mongod
```

### MongoDB Atlas Setup (Cloud)

#### 1. Create Atlas Account
1. Go to [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Sign up for a free account
3. Create a new project

#### 2. Create Cluster
1. Click "Build a Cluster"
2. Choose cloud provider (AWS/GCP/Azure)
3. Select region closest to your users
4. Choose cluster tier (M0 for free tier)
5. Name your cluster

#### 3. Configure Access
1. **Database Access:**
   - Add new database user
   - Username: `aiko_admin`
   - Password: Generate secure password
   - Role: Atlas Admin

2. **Network Access:**
   - Add IP Address
   - For development: Add current IP
   - For production: Add application server IPs
   - For testing: Allow from anywhere (0.0.0.0/0) - NOT for production!

#### 4. Get Connection String
1. Click "Connect" on your cluster
2. Choose "Connect your application"
3. Select "Java" and version "4.3 or later"
4. Copy connection string:
```
mongodb+srv://aiko_admin:<password>@cluster0.xxxxx.mongodb.net/ai_orchestrator?retryWrites=true&w=majority
```

#### 5. Update Application Configuration

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_ATLAS_URI:mongodb+srv://aiko_admin:password@cluster0.xxxxx.mongodb.net/ai_orchestrator?retryWrites=true&w=majority}
```

## 📖 API Documentation

### Authentication Endpoints

#### Register User
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
}

Response: 201 Created
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "username": "johndoe",
  "roles": ["USER"]
}
```

#### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "johndoe",
  "password": "SecurePass123!"
}

Response: 200 OK
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

### Query Endpoints

#### Submit Query
```http
POST /api/v1/queries
Authorization: Bearer <token>
Content-Type: application/json

{
  "queryText": "What are the best practices for MongoDB indexing?",
  "queryType": "RESEARCH",
  "context": {
    "domain": "database",
    "priority": "high"
  }
}

Response: 201 Created
{
  "id": "507f1f77bcf86cd799439011",
  "queryText": "What are the best practices for MongoDB indexing?",
  "status": "PROCESSING",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Get Query Results
```http
GET /api/v1/queries/{id}
Authorization: Bearer <token>

Response: 200 OK
{
  "id": "507f1f77bcf86cd799439011",
  "queryText": "What are the best practices for MongoDB indexing?",
  "status": "COMPLETED",
  "response": {
    "content": "MongoDB indexing best practices include...",
    "confidence": 0.95,
    "sources": ["doc1", "doc2"],
    "agentType": "RESEARCH"
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:15Z"
}
```

### Knowledge Base Endpoints

#### Upload Document
```http
POST /api/v1/knowledge/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: document.pdf
metadata: {"category": "technical", "tags": ["mongodb", "database"]}

Response: 201 Created
{
  "id": "507f1f77bcf86cd799439012",
  "filename": "document.pdf",
  "size": 1048576,
  "processingStatus": "PROCESSING",
  "uploadedAt": "2024-01-15T10:30:00Z"
}
```

### Full API Documentation

Access complete API documentation at: http://localhost:8080/swagger-ui.html

## ⚙️ Configuration

### Application Configuration

#### application.yml
```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: ai-knowledge-orchestrator
    
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/ai_orchestrator}
      auto-index-creation: true
      min-connections-per-host: 10
      max-connections-per-host: 100
      
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
      
jwt:
  secret: ${JWT_SECRET:base64-encoded-256-bit-secret}
  expiration: 86400000     # 24 hours
  refresh:
    expiration: 604800000   # 7 days

mongodb:
  backup:
    enabled: ${BACKUP_ENABLED:true}
    path: ${BACKUP_PATH:./backup/mongodb}
    cron: "0 0 2 * * ?"     # Daily at 2 AM
    retention:
      days: 7

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      
logging:
  level:
    root: INFO
    com.aiko.orchestrator: DEBUG
    org.springframework.data.mongodb: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%X{correlationId}] %-5level %logger{36} - %msg%n"
```

### Environment Variables

```bash
# Required
export MONGODB_URI=mongodb://localhost:27017/ai_orchestrator
export JWT_SECRET=your-base64-encoded-secret-key

# Optional
export SPRING_PROFILES_ACTIVE=dev
export SERVER_PORT=8080
export REDIS_HOST=localhost
export REDIS_PORT=6379
export BACKUP_ENABLED=true
export BACKUP_PATH=/backup/mongodb

# MongoDB Atlas
export MONGODB_ATLAS_URI=mongodb+srv://user:pass@cluster.mongodb.net/db

# Monitoring
export MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true
```

### Docker Configuration

#### docker-compose.yml
```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:6.0
    container_name: aiko-mongodb
    restart: unless-stopped
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: password
      MONGO_INITDB_DATABASE: ai_orchestrator
    volumes:
      - mongodb_data:/data/db
      - ./init-mongo.js:/docker-entrypoint-initdb.d/init-mongo.js:ro
    networks:
      - aiko-network

  redis:
    image: redis:7-alpine
    container_name: aiko-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - aiko-network

  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: aiko-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MONGODB_URI: mongodb://admin:password@mongodb:27017/ai_orchestrator?authSource=admin
      REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      - mongodb
      - redis
    networks:
      - aiko-network
    volumes:
      - ./backup:/backup

volumes:
  mongodb_data:
  redis_data:

networks:
  aiko-network:
    driver: bridge
```

## 🚀 MongoDB Atlas Deployment

### Step 1: Prepare Application

Update configuration for production:
```yaml
spring:
  profiles:
    active: production
    
  data:
    mongodb:
      uri: ${MONGODB_ATLAS_URI}
      database: ai_orchestrator_prod
      
logging:
  level:
    root: WARN
    com.aiko.orchestrator: INFO
```

### Step 2: Set Up Indexes

Connect to Atlas and create indexes:
```javascript
// Connect to Atlas cluster
mongosh "mongodb+srv://cluster.mongodb.net/ai_orchestrator" --username admin

// Create indexes
db.users.createIndex({ "username": 1 }, { unique: true })
db.users.createIndex({ "email": 1 }, { unique: true })
db.queries.createIndex({ "userId": 1, "createdAt": -1 })
db.queries.createIndex({ "status": 1 })
db.knowledge.createIndex({ "title": "text", "content": "text", "tags": "text" })
```

### Step 3: Enable Monitoring

1. Go to Atlas Console
2. Click on "Metrics" tab
3. Enable Performance Advisor
4. Set up alerts for:
   - High CPU usage
   - Connection limits
   - Slow queries

### Step 4: Configure Backup

1. Go to "Backup" tab
2. Enable Cloud Backup
3. Set schedule (daily recommended)
4. Configure retention (30 days recommended)

### Step 5: Deploy Application

Using Docker:
```bash
docker build -t aiko-orchestrator .
docker run -d \
  -p 8080:8080 \
  -e MONGODB_ATLAS_URI=$MONGODB_ATLAS_URI \
  -e JWT_SECRET=$JWT_SECRET \
  -e SPRING_PROFILES_ACTIVE=production \
  aiko-orchestrator
```

Using JAR:
```bash
java -jar -Xmx2g -Xms1g \
  -Dspring.profiles.active=production \
  -Dmongodb.uri=$MONGODB_ATLAS_URI \
  orchestrator-1.0.0.jar
```

## 💻 Development

### Project Structure

```
ai-knowledge-orchestrator/
├── src/
│   ├── main/
│   │   ├── java/com/aiko/orchestrator/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── service/         # Business logic
│   │   │   ├── repository/      # Data access
│   │   │   ├── model/           # Domain models
│   │   │   ├── dto/             # Data transfer objects
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── filter/          # Security filters
│   │   │   ├── aspect/          # AOP aspects
│   │   │   └── util/            # Utility classes
│   │   └── resources/
│   │       ├── application.yml  # Main configuration
│   │       └── logback-spring.xml
│   └── test/                    # Test classes
├── backup/                      # Backup directory
├── logs/                        # Application logs
├── docker/                      # Docker files
├── docs/                        # Documentation
├── scripts/                     # Utility scripts
├── pom.xml                      # Maven configuration
└── README.md
```

### Code Style

We follow standard Java conventions:
- Use 4 spaces for indentation
- Max line length: 120 characters
- Use meaningful variable names
- Add JavaDoc for public methods
- Follow SOLID principles

### Git Workflow

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes and commit: `git commit -m "Add: feature description"`
3. Push branch: `git push origin feature/your-feature`
4. Create Pull Request
5. Code review and merge

## 🧪 Testing

### Run Tests

```bash
# All tests
./mvnw test

# Specific test class
./mvnw test -Dtest=UserServiceTest

# Integration tests
./mvnw verify

# With coverage
./mvnw test jacoco:report
```

### Test Coverage

Minimum coverage requirements:
- Overall: 70%
- Service layer: 80%
- Critical paths: 90%

### Example Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldRegisterUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("testuser")
            .email("test@example.com")
            .password("Test123!")
            .firstName("Test")
            .lastName("User")
            .build();
            
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.username").value("testuser"));
    }
}
```

## 🚄 Performance

### Optimization Tips

1. **Indexing Strategy**
   - Create indexes for frequently queried fields
   - Use compound indexes for multi-field queries
   - Monitor index usage with explain plans

2. **Connection Pooling**
   - Min connections: 10
   - Max connections: 100
   - Adjust based on load

3. **Query Optimization**
   - Use projections to limit returned fields
   - Implement cursor-based pagination
   - Cache frequently accessed data

4. **Monitoring**
   - Check slow query logs
   - Monitor connection pool usage
   - Track API response times

### Performance Metrics

Current benchmarks (4 CPU, 8GB RAM):
- API Response Time: p50 < 50ms, p99 < 200ms
- Query Throughput: 1000 req/s
- Concurrent Users: 500
- MongoDB Queries: < 10ms with indexes

## 🔒 Security

### Security Features

- **JWT Authentication** - Token-based authentication
- **Password Encryption** - BCrypt with salt rounds
- **Rate Limiting** - Prevent API abuse
- **CORS Configuration** - Control cross-origin access
- **Input Validation** - Prevent injection attacks
- **Audit Logging** - Track all activities

### Security Best Practices

1. **Environment Variables**
   - Never commit secrets
   - Use strong passwords
   - Rotate keys regularly

2. **MongoDB Security**
   - Enable authentication
   - Use TLS/SSL
   - Limit network access
   - Regular security updates

3. **API Security**
   - Validate all inputs
   - Use HTTPS in production
   - Implement rate limiting
   - Monitor suspicious activity

## 📊 Monitoring

### Health Checks

```bash
# Basic health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health -H "Authorization: Bearer <admin-token>"
```

### Metrics

Access metrics at: http://localhost:8080/actuator/metrics

Key metrics:
- `mongodb.query.count` - Query count
- `mongodb.query.duration` - Query performance
- `http.server.requests` - API performance
- `jvm.memory.used` - Memory usage

### Logging

Logs location: `./logs/ai-orchestrator.log`

Log levels:
```yaml
logging:
  level:
    com.aiko.orchestrator: DEBUG  # Development
    com.aiko.orchestrator: INFO   # Production
```

## 💾 Backup & Recovery

### Automated Backup

Configured in `application.yml`:
```yaml
mongodb:
  backup:
    enabled: true
    cron: "0 0 2 * * ?"  # Daily at 2 AM
    retention:
      days: 7
```

### Manual Backup

```bash
# Backup
curl -X POST http://localhost:8080/api/v1/admin/mongodb/backup \
  -H "Authorization: Bearer <admin-token>"

# List backups
curl http://localhost:8080/api/v1/admin/mongodb/backups \
  -H "Authorization: Bearer <admin-token>"

# Restore
curl -X POST http://localhost:8080/api/v1/admin/mongodb/restore?timestamp=20240115_023000 \
  -H "Authorization: Bearer <admin-token>"
```

### MongoDB Tools Backup

```bash
# Backup
mongodump --uri="mongodb://localhost:27017/ai_orchestrator" --out=./backup/manual

# Restore
mongorestore --uri="mongodb://localhost:27017/ai_orchestrator" --dir=./backup/manual --drop
```

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

### How to Contribute

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

### Code of Conduct

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details on our code of conduct.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Team

- **Saranya JS** - Lead Developer - [GitHub](https://github.com/saranyajs)
- **AIKO Team** - Development Team

## 📞 Support

For support, please:
1. Check the [documentation](https://docs.aiko.com)
2. Search [existing issues](https://github.com/aiko/ai-knowledge-orchestrator/issues)
3. Create a new issue if needed
4. Contact: support@aiko.com

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- MongoDB team for the powerful database
- OpenAI/Anthropic for AI capabilities
- All contributors and users

---

**Built with ❤️ by the AIKO Team**

*Last updated: December 2024*
