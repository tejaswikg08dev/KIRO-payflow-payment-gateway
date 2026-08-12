# Sprint 1, Part 18: Docker Services

**Duration:** 2-3 hours  
**Prerequisites:** Part 17 completed, Docker Desktop installed

---

## 1. What We're Building

In this part, you'll understand the **Docker configuration** for PayFlow services.

> **Sprint 1 Focus:** For Sprint 1 (Auth & Onboarding), use `docker-compose-infra.yml` which provides PostgreSQL, Redis, DynamoDB Local, and LocalStack. Run Java services locally via IDE for easier debugging.

### Sprint 1: Infrastructure Only (docker-compose-infra.yml)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 SPRINT 1: docker-compose-infra.yml                           │
│                                                                              │
│  Infrastructure Layer (run with: docker compose -f docker-compose-infra.yml up -d)
│  ┌────────────────┐  ┌────────────────┐                                    │
│  │ PostgreSQL     │  │ Redis          │                                    │
│  │ :5432          │  │ :6379          │                                    │
│  │ payflow-       │  │ payflow-redis  │                                    │
│  │ postgres       │  │                │                                    │
│  └────────────────┘  └────────────────┘                                    │
│                                                                              │
│  ┌────────────────┐  ┌────────────────┐                                    │
│  │ DynamoDB Local │  │ LocalStack     │                                    │
│  │ :8000          │  │ :4566          │                                    │
│  │ payflow-       │  │ SQS + SNS      │                                    │
│  │ dynamodb       │  │ payflow-       │                                    │
│  │                │  │ localstack     │                                    │
│  └────────────────┘  └────────────────┘                                    │
│                                                                              │
│  Application Layer (run locally via IDE or mvn spring-boot:run)            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                           │
│  │ service-   │  │ identity-  │  │ merchant-  │                           │
│  │ registry   │  │ service    │  │ service    │                           │
│  │ :8761      │  │ :8081      │  │ :8082      │                           │
│  └────────────┘  └────────────┘  └────────────┘                           │
│                                                                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────────┐                       │
│  │ config-    │  │ api-       │  │ frontend-      │                       │
│  │ server     │  │ gateway    │  │ dashboard      │                       │
│  │ :8888      │  │ :8080      │  │ :3000          │                       │
│  └────────────┘  └────────────┘  └────────────────┘                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Full Stack: docker-compose.yml (Sprint 2+)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 FULL STACK: docker-compose.yml                               │
│                                                                              │
│  Infrastructure Layer (data-net)                                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                      │
│  │ postgres │ │ redis    │ │zookeeper │ │ kafka    │                      │
│  │ :5432    │ │ :6379    │ │ :2181    │ │ :9092    │                      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘                      │
│                                                                              │
│  Backend Layer (backend-net)                                                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                      │
│  │ service- │ │ config-  │ │ identity-│ │ merchant-│                      │
│  │ registry │ │ server   │ │ service  │ │ service  │                      │
│  │ :8761    │ │ :8888    │ │ :8081    │ │ :8082    │                      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘                      │
│                                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                                    │
│  │ payment- │ │ api-     │ │ bank-    │                                    │
│  │ service  │ │ gateway  │ │ simulator│                                    │
│  │ :8083    │ │ :8080    │ │ :9000    │                                    │
│  └──────────┘ └──────────┘ └──────────┘                                    │
│                                                                              │
│  Frontend Layer (frontend-net)                                              │
│  ┌──────────────┐ ┌──────────────┐                                        │
│  │ merchant-    │ │ hosted-      │                                        │
│  │ portal :3000 │ │ checkout     │                                        │
│  │              │ │ :3001        │                                        │
│  └──────────────┘ └──────────────┘                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### When to Use Each File

| File | Services | Use Case |
|------|----------|----------|
| `docker-compose-infra.yml` | PostgreSQL, Redis, DynamoDB Local, LocalStack | **Sprint 1**: Run infra in Docker, services locally via IDE |
| `docker-compose.yml` | All services containerized + Kafka/Zookeeper | **Sprint 2+**: Full integration testing |

---

## 2. Concepts Deep Dive

### 2.1 Multi-stage Docker Builds

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MULTI-STAGE BUILD EXPLAINED                               │
│                                                                              │
│  Stage 1: BUILD                          Stage 2: RUNTIME                   │
│  ─────────────────                       ──────────────────                  │
│  FROM maven:3.9-eclipse-temurin-17      FROM eclipse-temurin:21-jre-alpine │
│                                                                              │
│  • Full JDK (build tools)                • JRE only (no compiler)           │
│  • Maven installed                       • No build tools                   │
│  • All dependencies                      • Just the JAR file                │
│  • Size: ~800MB                          • Size: ~200MB                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    BUILD FLOW                                      │    │
│  │                                                                     │    │
│  │  Source Code                                                        │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  Stage 1 (build)                                                    │    │
│  │  ├── Copy pom.xml files                                             │    │
│  │  ├── Download dependencies (cached!)                               │    │
│  │  ├── Copy source code                                               │    │
│  │  └── mvn package → app.jar                                          │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  Stage 2 (runtime)                                                  │    │
│  │  ├── COPY --from=build app.jar                                      │    │
│  │  ├── Create non-root user                                          │    │
│  │  └── ENTRYPOINT ["java", "-jar", "app.jar"]                        │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Benefits:                                                                  │
│  • Smaller final image (no build tools)                                    │
│  • Faster deployments (smaller to transfer)                                │
│  • Better security (less attack surface)                                   │
│  • Cached dependency layer (faster rebuilds)                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Docker Network Isolation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NETWORK SEGMENTATION                                      │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                                                                        │ │
│  │   data-net (internal: true)     ← No external access to databases    │ │
│  │   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                    │ │
│  │   │  postgres   │ │   redis     │ │   kafka     │                    │ │
│  │   └─────────────┘ └─────────────┘ └─────────────┘                    │ │
│  │         ▲               ▲               ▲                             │ │
│  │         │               │               │                             │ │
│  │   ═══════════════════════════════════════════                        │ │
│  │                                                                        │ │
│  │   backend-net            ← Services communicate here                  │ │
│  │   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                    │ │
│  │   │  identity   │ │  payment    │ │  api-gateway│                    │ │
│  │   └─────────────┘ └─────────────┘ └─────────────┘                    │ │
│  │                           │                                           │ │
│  │   ═══════════════════════════════════════════════════════════════    │ │
│  │                           │                                           │ │
│  │   frontend-net           ← Only gateway exposed to frontend          │ │
│  │                           │                                           │ │
│  │                    [External Access]                                  │ │
│  │                                                                        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Docker Compose Files

PayFlow uses multiple compose files for different environments:

| File | Purpose | When to Use |
|------|---------|-------------|
| `docker-compose-infra.yml` | Infrastructure only (PostgreSQL, Redis, DynamoDB, LocalStack) | **Sprint 1 local development** |
| `docker-compose.yml` | Full stack with all services + Kafka/Zookeeper | Integration testing, Sprint 2+ |
| `docker-compose.prod.yml` | Production-optimized configuration | Production deployment |

### Infrastructure Services Summary

| Service | Port | Image | Purpose |
|---------|------|-------|---------|
| PostgreSQL | 5432 | postgres:15 | Main database (identity, merchant, payment schemas) |
| Redis | 6379 | redis:7-alpine | Caching, rate limiting, idempotency |
| DynamoDB Local | 8000 | amazon/dynamodb-local | Webhook events, routing metrics, audit trail |
| LocalStack | 4566 | localstack/localstack | SQS + SNS simulation (notifications) |
| Zookeeper | 2181 | confluentinc/cp-zookeeper:7.5.0 | Kafka coordination (docker-compose.yml only) |
| Kafka | 9092 | confluentinc/cp-kafka:7.5.0 | Event streaming (docker-compose.yml only) |

---

## 4. Step-by-Step: Infrastructure Only

### Step 4.1: docker-compose-infra.yml (Actual File)

**File: `docker-compose-infra.yml`**

```yaml
# Docker Compose — Infrastructure Only
# Run this first to start databases and messaging services locally.
# Usage: docker compose -f docker-compose-infra.yml up -d
#
# This starts:
# - PostgreSQL (port 5432) — our main relational database
# - Redis (port 6379) — caching, idempotency, rate limiting
# - DynamoDB Local (port 8000) — webhook events, routing metrics
# - LocalStack (port 4566) — simulates AWS SQS and SNS locally

version: '3.8'

services:

  # ===== PostgreSQL Database =====
  # Stores: users, merchants, payments, settlements
  # Access: localhost:5432
  # Credentials: payflow / payflow_secret
  postgres:
    image: postgres:15
    container_name: payflow-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: payflow
      POSTGRES_USER: payflow
      POSTGRES_PASSWORD: payflow_secret
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payflow"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ===== Redis Cache =====
  # Stores: idempotency keys, rate limit counters, JWT blacklist, routing cache
  # Access: localhost:6379
  redis:
    image: redis:7-alpine
    container_name: payflow-redis
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ===== DynamoDB Local =====
  # Stores: webhook events, routing metrics, audit trail
  # Access: localhost:8000
  # This is Amazon's official local DynamoDB emulator
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    container_name: payflow-dynamodb
    ports:
      - "8000:8000"
    command: "-jar DynamoDBLocal.jar -sharedDb -inMemory"

  # ===== LocalStack (SQS + SNS) =====
  # Simulates AWS services locally
  # SQS endpoint: http://localhost:4566
  # SNS endpoint: http://localhost:4566
  localstack:
    image: localstack/localstack:latest
    container_name: payflow-localstack
    ports:
      - "4566:4566"
    environment:
      SERVICES: sqs,sns
      DEFAULT_REGION: ap-south-1
      DOCKER_HOST: unix:///var/run/docker.sock
    volumes:
      - ./docker/init-localstack.sh:/etc/localstack/init/ready.d/init.sh

volumes:
  postgres_data:
```

**Configuration Explained:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    KEY CONFIGURATION VALUES                                  │
│                                                                              │
│  PostgreSQL:                                                                │
│  ───────────                                                                │
│  • Database: payflow                                                        │
│  • User: payflow                                                            │
│  • Password: payflow_secret                                                 │
│  • Port: 5432                                                               │
│  • Container: payflow-postgres                                              │
│  • Init script: ./docker/init-db.sql (creates schemas)                     │
│                                                                              │
│  Redis:                                                                     │
│  ──────                                                                     │
│  • Port: 6379                                                               │
│  • Container: payflow-redis                                                 │
│  • Max memory: 128MB                                                        │
│  • Eviction: allkeys-lru (remove least recently used)                      │
│                                                                              │
│  DynamoDB Local:                                                            │
│  ───────────────                                                            │
│  • Port: 8000                                                               │
│  • Container: payflow-dynamodb                                              │
│  • Mode: In-memory (data lost on restart)                                  │
│  • Use: Webhook events, routing metrics, audit trail                       │
│                                                                              │
│  LocalStack (AWS Simulator):                                               │
│  ───────────────────────────                                                │
│  • Port: 4566 (unified endpoint for all AWS services)                      │
│  • Container: payflow-localstack                                           │
│  • Services: SQS (queues), SNS (notifications)                             │
│  • Region: ap-south-1                                                       │
│                                                                              │
│  These values MUST match your application.yml files!                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Start Infrastructure

```powershell
# Start infrastructure services only
docker compose -f docker-compose-infra.yml up -d

# Verify services are healthy
docker compose -f docker-compose-infra.yml ps

# Expected output:
# payflow-postgres    running (healthy)
# payflow-redis       running (healthy)
# payflow-dynamodb    running
# payflow-localstack  running

# Verify PostgreSQL connection
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt"

# Verify Redis connection
docker exec -it payflow-redis redis-cli ping
# Expected: PONG

# Verify DynamoDB Local
curl http://localhost:8000

# Verify LocalStack
curl http://localhost:4566/_localstack/health
```

---

## 5. Step-by-Step: Java Service Dockerfile

### Step 5.1: Identity Service Dockerfile

**File: `identity-service/Dockerfile`**

```dockerfile
# ============================================
# PayFlow Identity Service - Multi-stage Build
# ============================================

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy parent POM and common-lib for dependency resolution
COPY pom.xml ./pom.xml
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
COPY identity-service/pom.xml ./identity-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl identity-service -am -B

# Copy source code
COPY identity-service/src ./identity-service/src

# Build the application
RUN mvn package -pl identity-service -am -DskipTests -B \
    && mv identity-service/target/*.jar /app/app.jar

# Stage 2: Runtime with JRE only
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create non-root user
RUN addgroup -S payflow && adduser -S payflow -G payflow

# Install curl for health checks
RUN apk add --no-cache curl

# Copy artifact from build stage
COPY --from=build /app/app.jar ./app.jar

# Set ownership
RUN chown payflow:payflow app.jar
USER payflow

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Expose service port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Dockerfile Line-by-Line:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DOCKERFILE EXPLAINED                                      │
│                                                                              │
│  Build Stage:                                                               │
│  ────────────                                                               │
│  FROM maven:3.9-eclipse-temurin-17-alpine AS build                         │
│       │                                                                     │
│       └── Maven 3.9 + JDK 17 + Alpine Linux (small base)                   │
│                                                                              │
│  RUN mvn dependency:go-offline -pl identity-service -am -B                 │
│       │    │                   │                     │  │                   │
│       │    │                   │                     │  └── Batch mode     │
│       │    │                   │                     └── Also make deps    │
│       │    │                   └── Just identity-service                   │
│       │    └── Download all dependencies                                    │
│       └── Run Maven                                                         │
│                                                                              │
│  Runtime Stage:                                                             │
│  ──────────────                                                             │
│  FROM eclipse-temurin:21-jre-alpine                                        │
│       │                   │                                                 │
│       │                   └── JRE only (no compiler) + Alpine              │
│       └── Eclipse Temurin JRE (official OpenJDK)                           │
│                                                                              │
│  RUN addgroup -S payflow && adduser -S payflow -G payflow                  │
│       │                                                                     │
│       └── Create system user (no password, no home)                        │
│           Never run as root in production!                                  │
│                                                                              │
│  USER payflow                                                               │
│       │                                                                     │
│       └── Switch to non-root user for security                             │
│                                                                              │
│  JAVA_OPTS explained:                                                       │
│  ───────────────────                                                        │
│  -XX:+UseContainerSupport   → JVM respects container limits                │
│  -XX:MaxRAMPercentage=75.0  → Use 75% of container memory                  │
│  -XX:+UseG1GC               → Use G1 garbage collector                     │
│  -XX:+HeapDumpOnOutOfMemoryError → Create dump on OOM                      │
│                                                                              │
│  HEALTHCHECK:                                                               │
│  ────────────                                                               │
│  interval=30s    → Check every 30 seconds                                  │
│  start-period=40s → Wait 40s before first check (JVM startup)              │
│  retries=3       → Mark unhealthy after 3 failures                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Full Stack Docker Compose

### Step 6.1: Key Service Definitions

**From: `docker-compose.yml`**

```yaml
services:
  # Infrastructure
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: payflow
      POSTGRES_USER: payflow
      POSTGRES_PASSWORD: payflow_secret
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payflow -d payflow"]
    networks:
      - data-net

  # Application Services
  identity-service:
    build:
      context: .
      dockerfile: identity-service/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payflow
      SPRING_DATASOURCE_USERNAME: payflow
      SPRING_DATASOURCE_PASSWORD: payflow_secret
      SPRING_DATA_REDIS_HOST: redis
      JWT_SECRET: dev-jwt-secret-key-minimum-32-characters-long
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - backend-net
      - data-net

  api-gateway:
    build:
      context: .
      dockerfile: api-gateway/Dockerfile
    ports:
      - "8080:8080"
    environment:
      IDENTITY_SERVICE_URL: http://identity-service:8081
      PAYMENT_SERVICE_URL: http://payment-service:8082
    depends_on:
      identity-service:
        condition: service_healthy
    networks:
      - frontend-net
      - backend-net

networks:
  frontend-net:    # External access
  backend-net:     # Service-to-service
  data-net:
    internal: true # No external access
```

**Service Dependencies:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE STARTUP ORDER                                     │
│                                                                              │
│  1. Infrastructure (parallel)                                               │
│     ├── postgres                                                            │
│     ├── redis                                                               │
│     ├── zookeeper → kafka                                                   │
│                                                                              │
│  2. Spring Cloud Infrastructure                                             │
│     ├── service-registry (waits for nothing)                               │
│     └── config-server (waits for service-registry)                         │
│                                                                              │
│  3. Business Services (parallel, wait for infra)                            │
│     ├── identity-service (waits for postgres, redis, config-server)        │
│     ├── payment-service (waits for postgres, redis, kafka, bank-simulator) │
│     └── bank-simulator (waits for nothing)                                  │
│                                                                              │
│  4. API Gateway                                                             │
│     └── api-gateway (waits for identity-service, payment-service)          │
│                                                                              │
│  depends_on with condition:                                                 │
│  ──────────────────────────                                                 │
│  condition: service_healthy → Wait until healthcheck passes                │
│  condition: service_started → Wait until container starts (less reliable)  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Verification

### Start Full Stack

```powershell
# Build all images
docker compose build

# Start all services
docker compose up -d

# Watch logs
docker compose logs -f

# Check status
docker compose ps
```

### Verify Health

```powershell
# Check individual service health
docker inspect payflow-identity --format='{{.State.Health.Status}}'
# Expected: healthy

# Check all services
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

### Stop Services

```powershell
# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v
```

---

## 8. File Structure

```
KIRO-payflow-payment-gateway/
├── docker-compose.yml              ← Full stack (all services + Kafka/Zookeeper)
├── docker-compose-infra.yml        ← Infrastructure only (Sprint 1)
├── docker-compose.prod.yml         ← Production configuration
├── docker/
│   ├── init-db.sql                 ← PostgreSQL init (creates schemas)
│   └── init-localstack.sh          ← AWS services init (SQS queues, SNS topics)
│
│ Services with Dockerfiles:
├── api-gateway/
│   └── Dockerfile
├── bank-simulator/
│   └── Dockerfile
├── config-server/
│   └── Dockerfile
├── identity-service/
│   └── Dockerfile
├── merchant-service/
│   └── Dockerfile
├── notification-service/
│   └── Dockerfile
├── payment-service/
│   └── Dockerfile
├── routing-service/
│   └── Dockerfile
├── service-registry/
│   └── Dockerfile
├── settlement-service/
│   └── Dockerfile
├── webhook-service/
│   └── Dockerfile
│
│ Frontend applications (no Dockerfile in Sprint 1, run via npm):
├── frontend-dashboard/             ← Merchant portal (Sprint 1)
├── frontend-checkout/              ← Hosted checkout page
└── frontend-developer-portal/      ← Developer documentation
```

### Services by Sprint

| Sprint | Services |
|--------|----------|
| Sprint 1 | service-registry, config-server, api-gateway, identity-service, merchant-service, frontend-dashboard |
| Sprint 2+ | payment-service, bank-simulator, routing-service, webhook-service, notification-service, settlement-service |

---

## 9. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ┌────────────────────────┬────────────────────────────────────────────┐   │
│  │  Concept               │  Implementation                            │   │
│  ├────────────────────────┼────────────────────────────────────────────┤   │
│  │  Multi-stage builds    │  Build stage + Runtime stage               │   │
│  │  Layer caching         │  Copy pom.xml before src for cache         │   │
│  │  Non-root user         │  adduser + USER directive                  │   │
│  │  Health checks         │  HEALTHCHECK + curl actuator               │   │
│  │  Network isolation     │  data-net internal, frontend-net external  │   │
│  │  Service dependencies  │  depends_on with condition: service_healthy│   │
│  │  JVM tuning            │  UseContainerSupport, MaxRAMPercentage     │   │
│  │  AWS Local Dev         │  DynamoDB Local + LocalStack (SQS/SNS)     │   │
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Sprint 1 Workflow:                                                         │
│  ─────────────────                                                          │
│  1. docker compose -f docker-compose-infra.yml up -d                       │
│  2. Run Java services locally via IDE (easier debugging)                   │
│  3. npm run dev for frontend-dashboard                                     │
│                                                                              │
│  Infrastructure Services (docker-compose-infra.yml):                       │
│  ──────────────────────────────────────────────────                         │
│  • PostgreSQL (:5432) - Main database                                      │
│  • Redis (:6379) - Caching, rate limiting                                  │
│  • DynamoDB Local (:8000) - Webhook events, audit trail                    │
│  • LocalStack (:4566) - SQS queues, SNS topics                             │
│                                                                              │
│  Full Stack (docker-compose.yml) adds:                                     │
│  ─────────────────────────────────────                                      │
│  • Zookeeper (:2181) - Kafka coordination                                  │
│  • Kafka (:9092) - Event streaming for payment-service                     │
│  • All backend services containerized                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **docker-compose-infra.yml** | PostgreSQL, Redis, DynamoDB Local, LocalStack |
| **docker-compose.yml** | Full stack + Kafka/Zookeeper (Sprint 2+) |
| **Multi-stage** | Smaller images, better security |
| **depends_on** | Use `condition: service_healthy` |
| **Networks** | Isolate data layer with `internal: true` |
| **AWS Local** | LocalStack for SQS/SNS, DynamoDB Local for NoSQL |

---

## 10. Common Issues and Solutions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING GUIDE                                     │
│                                                                              │
│  Issue 1: "Service unhealthy, retrying..."                                 │
│  ─────────────────────────────────────────                                  │
│  Cause:   Healthcheck failing, often JVM still starting                    │
│  Fix:     Increase start_period in healthcheck                             │
│           Check logs: docker logs payflow-identity                         │
│                                                                              │
│  Issue 2: "Connection refused to postgres:5432"                            │
│  ──────────────────────────────────────────────                             │
│  Cause:   Service trying to connect before postgres is ready               │
│  Fix:     Ensure depends_on uses condition: service_healthy                │
│                                                                              │
│  Issue 3: "Out of memory" during build                                     │
│  ──────────────────────────────────────────                                 │
│  Cause:   Docker doesn't have enough memory allocated                      │
│  Fix:     Docker Desktop → Settings → Resources → Increase memory          │
│                                                                              │
│  Issue 4: "Port already in use"                                            │
│  ────────────────────────────                                               │
│  Cause:   Another service using same port                                  │
│  Fix:     Stop local services or change port mapping in compose            │
│           netstat -ano | findstr :8080                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Next Steps

In the next part, you'll explore the **CI/CD pipeline** with GitHub Actions.

**Continue to:** [part-19-cicd-backend.md](./part-19-cicd-backend.md)

---

**End of Sprint 1, Part 18**
