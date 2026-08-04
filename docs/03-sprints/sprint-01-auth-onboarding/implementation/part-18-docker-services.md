# Sprint 1, Part 18: Docker Services

**Duration:** 2-3 hours  
**Prerequisites:** Part 17 completed, Docker Desktop installed

---

## 1. What We're Building

In this part, you'll understand the **Docker configuration** for all PayFlow services.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DOCKER ARCHITECTURE                                      │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    docker-compose.yml                                │   │
│  │                                                                      │   │
│  │  Infrastructure Layer (data-net)                                    │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐   │   │
│  │  │ postgres   │  │ redis      │  │ zookeeper  │  │ kafka      │   │   │
│  │  │ :5432      │  │ :6379      │  │ :2181      │  │ :9092      │   │   │
│  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘   │   │
│  │                                                                      │   │
│  │  Backend Layer (backend-net)                                        │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐   │   │
│  │  │ service-   │  │ config-    │  │ identity-  │  │ payment-   │   │   │
│  │  │ registry   │  │ server     │  │ service    │  │ service    │   │   │
│  │  │ :8761      │  │ :8888      │  │ :8081      │  │ :8082      │   │   │
│  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘   │   │
│  │                                                                      │   │
│  │  ┌────────────┐  ┌────────────┐                                    │   │
│  │  │ api-       │  │ bank-      │                                    │   │
│  │  │ gateway    │  │ simulator  │                                    │   │
│  │  │ :8080      │  │ :9090      │                                    │   │
│  │  └────────────┘  └────────────┘                                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Development Tip: Use docker-compose-infra.yml for infrastructure only    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

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

PayFlow uses two compose files:

| File | Purpose | When to Use |
|------|---------|-------------|
| `docker-compose-infra.yml` | Infrastructure only | Local development |
| `docker-compose.yml` | Full stack | Integration testing |

---

## 4. Step-by-Step: Infrastructure Only

### Step 4.1: docker-compose-infra.yml

**File: `docker-compose-infra.yml`**

```yaml
# Docker Compose — Infrastructure Only
# Usage: docker compose -f docker-compose-infra.yml up -d

version: '3.8'

services:

  # ===== PostgreSQL Database =====
  # Stores: users, merchants, payments, settlements
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
  # Stores: idempotency keys, rate limit counters, JWT blacklist
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
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    container_name: payflow-dynamodb
    ports:
      - "8000:8000"
    command: "-jar DynamoDBLocal.jar -sharedDb -inMemory"

  # ===== LocalStack (SQS + SNS) =====
  # Simulates AWS services locally
  localstack:
    image: localstack/localstack:latest
    container_name: payflow-localstack
    ports:
      - "4566:4566"
    environment:
      SERVICES: sqs,sns
      DEFAULT_REGION: ap-south-1
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
│  • Init script: ./docker/init-db.sql (creates schemas)                     │
│                                                                              │
│  Redis:                                                                     │
│  ──────                                                                     │
│  • Port: 6379                                                               │
│  • Max memory: 128MB                                                        │
│  • Eviction: allkeys-lru (remove least recently used)                      │
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
├── docker-compose.yml          ← Full stack
├── docker-compose-infra.yml    ← Infrastructure only
├── docker/
│   ├── init-db.sql             ← PostgreSQL init script
│   └── init-localstack.sh      ← AWS services init
├── identity-service/
│   └── Dockerfile
├── payment-service/
│   └── Dockerfile
├── api-gateway/
│   └── Dockerfile
└── ...other services
```

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
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Development Tip: Use docker-compose-infra.yml for daily development.      │
│  Run services locally with IDE debugger, connect to Docker databases.      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **Two compose files** | infra-only for dev, full for integration |
| **Multi-stage** | Smaller images, better security |
| **depends_on** | Use `condition: service_healthy` |
| **Networks** | Isolate data layer with `internal: true` |

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
