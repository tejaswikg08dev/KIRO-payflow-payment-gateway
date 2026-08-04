# Sprint 1, Part 06: Docker & CI/CD

**Duration:** 3-4 hours  
**Prerequisites:** Parts 01-05 completed

---

## 1. What We're Building

In this final part of Sprint 1, you'll:
- Create Dockerfiles for all services
- Set up docker-compose.yml for full stack
- Create GitHub Actions CI pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DOCKER ARCHITECTURE                                      │
│                                                                              │
│  docker-compose up                                                           │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Docker Network                                │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │   │
│  │  │ postgres     │  │ redis        │  │ localstack   │              │   │
│  │  │ :5432        │  │ :6379        │  │ :4566        │              │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐                                 │   │
│  │  │ service-     │  │ config-      │                                 │   │
│  │  │ registry     │  │ server       │                                 │   │
│  │  │ :8761        │  │ :8888        │                                 │   │
│  │  └──────────────┘  └──────────────┘                                 │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │   │
│  │  │ api-gateway  │  │ identity-    │  │ merchant-    │              │   │
│  │  │ :8080        │  │ service      │  │ service      │              │   │
│  │  │              │  │ :8081        │  │ :8082        │              │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Exposed to host: 8080 (API Gateway), 8761 (Eureka Dashboard)              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Dockerfiles

### 2.1 Service Registry Dockerfile

Create `service-registry/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# Service Registry (Eureka Server) Dockerfile
# ═══════════════════════════════════════════════════════════════════════════

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy parent POM first (for dependency caching)
COPY pom.xml ./
COPY service-registry/pom.xml ./service-registry/

# Download dependencies (cached if pom.xml unchanged)
RUN mvn dependency:go-offline -pl service-registry

# Copy source code
COPY service-registry/src ./service-registry/src

# Build the service
RUN mvn clean package -pl service-registry -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy JAR from builder
COPY --from=builder /app/service-registry/target/service-registry-*.jar app.jar

# Expose port
EXPOSE 8761

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8761/actuator/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.2 Config Server Dockerfile

Create `config-server/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# Config Server Dockerfile
# ═══════════════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml ./
COPY config-server/pom.xml ./config-server/

RUN mvn dependency:go-offline -pl config-server

COPY config-server/src ./config-server/src

RUN mvn clean package -pl config-server -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/config-server/target/config-server-*.jar app.jar

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.3 API Gateway Dockerfile

Create `api-gateway/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# API Gateway Dockerfile
# ═══════════════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml ./
COPY common-lib/pom.xml ./common-lib/
COPY api-gateway/pom.xml ./api-gateway/

# Build common-lib first (dependency)
COPY common-lib/src ./common-lib/src
RUN mvn clean install -pl common-lib -DskipTests

RUN mvn dependency:go-offline -pl api-gateway

COPY api-gateway/src ./api-gateway/src

RUN mvn clean package -pl api-gateway -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/api-gateway/target/api-gateway-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.4 Identity Service Dockerfile

Create `identity-service/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# Identity Service Dockerfile
# ═══════════════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml ./
COPY common-lib/pom.xml ./common-lib/
COPY identity-service/pom.xml ./identity-service/

COPY common-lib/src ./common-lib/src
RUN mvn clean install -pl common-lib -DskipTests

RUN mvn dependency:go-offline -pl identity-service

COPY identity-service/src ./identity-service/src

RUN mvn clean package -pl identity-service -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/identity-service/target/identity-service-*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.5 Merchant Service Dockerfile

Create `merchant-service/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# Merchant Service Dockerfile
# ═══════════════════════════════════════════════════════════════════════════

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml ./
COPY common-lib/pom.xml ./common-lib/
COPY merchant-service/pom.xml ./merchant-service/

COPY common-lib/src ./common-lib/src
RUN mvn clean install -pl common-lib -DskipTests

RUN mvn dependency:go-offline -pl merchant-service

COPY merchant-service/src ./merchant-service/src

RUN mvn clean package -pl merchant-service -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/merchant-service/target/merchant-service-*.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 3. Docker Compose

### 3.1 Full Stack docker-compose.yml

Create `docker-compose.yml` in project root:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# PayFlow Full Stack Docker Compose
# Sprint 1: Infrastructure + Auth Services
# ═══════════════════════════════════════════════════════════════════════════

version: '3.8'

services:
  # ─────────────────────────────────────────────────────────────────────────
  # Infrastructure Services
  # ─────────────────────────────────────────────────────────────────────────
  
  postgres:
    image: postgres:15-alpine
    container_name: payflow-postgres
    environment:
      POSTGRES_USER: payflow
      POSTGRES_PASSWORD: payflow123
      POSTGRES_DB: payflow
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payflow"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - payflow-network

  redis:
    image: redis:7-alpine
    container_name: payflow-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - payflow-network

  localstack:
    image: localstack/localstack:latest
    container_name: payflow-localstack
    ports:
      - "4566:4566"
    environment:
      - SERVICES=sqs,sns,dynamodb,s3
      - DEFAULT_REGION=ap-south-1
      - DATA_DIR=/var/lib/localstack/data
    volumes:
      - localstack_data:/var/lib/localstack
      - ./docker/init-localstack.sh:/etc/localstack/init/ready.d/init.sh
    networks:
      - payflow-network

  # ─────────────────────────────────────────────────────────────────────────
  # Spring Cloud Services
  # ─────────────────────────────────────────────────────────────────────────

  service-registry:
    build:
      context: .
      dockerfile: service-registry/Dockerfile
    container_name: payflow-service-registry
    ports:
      - "8761:8761"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - payflow-network

  config-server:
    build:
      context: .
      dockerfile: config-server/Dockerfile
    container_name: payflow-config-server
    ports:
      - "8888:8888"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/
    depends_on:
      service-registry:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - payflow-network

  # ─────────────────────────────────────────────────────────────────────────
  # Application Services
  # ─────────────────────────────────────────────────────────────────────────

  api-gateway:
    build:
      context: .
      dockerfile: api-gateway/Dockerfile
    container_name: payflow-api-gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      service-registry:
        condition: service_healthy
      config-server:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - payflow-network

  identity-service:
    build:
      context: .
      dockerfile: identity-service/Dockerfile
    container_name: payflow-identity-service
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/payflow
      - SPRING_DATASOURCE_USERNAME=payflow
      - SPRING_DATASOURCE_PASSWORD=payflow123
    depends_on:
      service-registry:
        condition: service_healthy
      config-server:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - payflow-network

  merchant-service:
    build:
      context: .
      dockerfile: merchant-service/Dockerfile
    container_name: payflow-merchant-service
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/payflow
      - SPRING_DATASOURCE_USERNAME=payflow
      - SPRING_DATASOURCE_PASSWORD=payflow123
    depends_on:
      service-registry:
        condition: service_healthy
      config-server:
        condition: service_healthy
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - payflow-network

# ─────────────────────────────────────────────────────────────────────────────
# Networks
# ─────────────────────────────────────────────────────────────────────────────
networks:
  payflow-network:
    driver: bridge

# ─────────────────────────────────────────────────────────────────────────────
# Volumes
# ─────────────────────────────────────────────────────────────────────────────
volumes:
  postgres_data:
  redis_data:
  localstack_data:
```


### 3.2 Database Initialization Script

Create `docker/init-db.sql`:

```sql
-- ═══════════════════════════════════════════════════════════════════════════
-- PayFlow Database Initialization Script
-- Creates schemas for all microservices
-- ═══════════════════════════════════════════════════════════════════════════

-- Create schemas for each service
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS ledger;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA identity TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA merchant TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA payment TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA ledger TO payflow;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

COMMENT ON DATABASE payflow IS 'PayFlow Payment Gateway Database';
```

### 3.3 LocalStack Initialization Script

Create `docker/init-localstack.sh`:

```bash
#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════
# LocalStack AWS Resources Initialization
# ═══════════════════════════════════════════════════════════════════════════

echo "Creating SQS queues..."
awslocal sqs create-queue --queue-name payment-events
awslocal sqs create-queue --queue-name webhook-events
awslocal sqs create-queue --queue-name notification-events

echo "Creating SNS topics..."
awslocal sns create-topic --name payment-notifications
awslocal sns create-topic --name merchant-alerts

echo "Creating DynamoDB tables..."
awslocal dynamodb create-table \
    --table-name api-keys \
    --attribute-definitions AttributeName=keyId,AttributeType=S \
    --key-schema AttributeName=keyId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

echo "Creating S3 buckets..."
awslocal s3 mb s3://payflow-documents
awslocal s3 mb s3://payflow-reports

echo "LocalStack initialization complete!"
```

---

## 4. GitHub Actions CI Pipeline

### 4.1 Why CI/CD?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CI/CD PIPELINE FLOW                                      │
│                                                                              │
│  Developer                                                                   │
│      │                                                                       │
│      │ git push                                                              │
│      ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     GitHub Actions                                   │   │
│  │                                                                      │   │
│  │  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐        │   │
│  │  │ Checkout │ → │  Build   │ → │   Test   │ → │  Analyze │        │   │
│  │  │   Code   │   │  Maven   │   │  JUnit   │   │  SonarQube│        │   │
│  │  └──────────┘   └──────────┘   └──────────┘   └──────────┘        │   │
│  │                                                                      │   │
│  │                       │                                              │   │
│  │                       ▼                                              │   │
│  │               ┌──────────────┐                                       │   │
│  │               │ Build Docker │                                       │   │
│  │               │    Images    │                                       │   │
│  │               └──────────────┘                                       │   │
│  │                       │                                              │   │
│  │                       ▼                                              │   │
│  │               ┌──────────────┐                                       │   │
│  │               │  Push to ECR │                                       │   │
│  │               │  (on main)   │                                       │   │
│  │               └──────────────┘                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Benefits:                                                                   │
│  • Catch bugs early before they reach production                            │
│  • Ensure consistent builds across environments                             │
│  • Automate repetitive tasks (testing, building, deploying)                │
│  • Provide visibility into code quality                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 4.2 Backend CI Workflow

Create `.github/workflows/ci-backend.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# PayFlow Backend CI Pipeline
# Triggered on push/PR to main branch for backend services
# ═══════════════════════════════════════════════════════════════════════════

name: Backend CI

# ─────────────────────────────────────────────────────────────────────────────
# TRIGGER CONDITIONS
# ─────────────────────────────────────────────────────────────────────────────
on:
  push:
    branches: [ main, develop ]
    paths:
      - '**/*.java'           # Any Java file changes
      - '**/pom.xml'          # Maven config changes
      - '.github/workflows/ci-backend.yml'  # This workflow
  pull_request:
    branches: [ main ]
    paths:
      - '**/*.java'
      - '**/pom.xml'

# ─────────────────────────────────────────────────────────────────────────────
# ENVIRONMENT VARIABLES
# ─────────────────────────────────────────────────────────────────────────────
env:
  JAVA_VERSION: '17'
  MAVEN_OPTS: '-Xmx1024m'

# ─────────────────────────────────────────────────────────────────────────────
# JOBS
# ─────────────────────────────────────────────────────────────────────────────
jobs:
  # ─────────────────────────────────────────────────────────────────────────
  # JOB 1: Build and Test
  # ─────────────────────────────────────────────────────────────────────────
  build:
    name: Build & Test
    runs-on: ubuntu-latest
    
    # Services needed for integration tests
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_USER: payflow
          POSTGRES_PASSWORD: payflow123
          POSTGRES_DB: payflow_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      # Step 1: Checkout code
      - name: Checkout Repository
        uses: actions/checkout@v4

      # Step 2: Set up Java
      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      # Step 3: Cache Maven dependencies
      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      # Step 4: Build all modules
      - name: Build with Maven
        run: mvn clean compile -DskipTests

      # Step 5: Run unit tests
      - name: Run Unit Tests
        run: mvn test -Dspring.profiles.active=test

      # Step 6: Run integration tests
      - name: Run Integration Tests
        run: mvn verify -Dspring.profiles.active=test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/payflow_test
          SPRING_DATASOURCE_USERNAME: payflow
          SPRING_DATASOURCE_PASSWORD: payflow123
          SPRING_DATA_REDIS_HOST: localhost

      # Step 7: Upload test results
      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: '**/target/surefire-reports/*.xml'

      # Step 8: Upload coverage report
      - name: Upload Coverage Report
        uses: actions/upload-artifact@v4
        if: success()
        with:
          name: coverage-report
          path: '**/target/site/jacoco/'


  # ─────────────────────────────────────────────────────────────────────────
  # JOB 2: Code Quality Analysis
  # ─────────────────────────────────────────────────────────────────────────
  code-quality:
    name: Code Quality
    runs-on: ubuntu-latest
    needs: build
    
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for SonarQube

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Run Checkstyle
        run: mvn checkstyle:check
        continue-on-error: true

      - name: Run SpotBugs
        run: mvn spotbugs:check
        continue-on-error: true

  # ─────────────────────────────────────────────────────────────────────────
  # JOB 3: Build Docker Images
  # ─────────────────────────────────────────────────────────────────────────
  docker:
    name: Build Docker Images
    runs-on: ubuntu-latest
    needs: build
    if: github.ref == 'refs/heads/main'
    
    strategy:
      matrix:
        service:
          - service-registry
          - config-server
          - api-gateway
          - identity-service
          - merchant-service

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build Docker Image
        run: |
          docker build \
            -f ${{ matrix.service }}/Dockerfile \
            -t payflow/${{ matrix.service }}:${{ github.sha }} \
            -t payflow/${{ matrix.service }}:latest \
            .

      # Uncomment when ECR is configured
      # - name: Configure AWS credentials
      #   uses: aws-actions/configure-aws-credentials@v4
      #   with:
      #     aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
      #     aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
      #     aws-region: ap-south-1
      
      # - name: Login to Amazon ECR
      #   id: login-ecr
      #   uses: aws-actions/amazon-ecr-login@v2

      # - name: Push to ECR
      #   run: |
      #     docker tag payflow/${{ matrix.service }}:latest \
      #       ${{ steps.login-ecr.outputs.registry }}/payflow/${{ matrix.service }}:latest
      #     docker push ${{ steps.login-ecr.outputs.registry }}/payflow/${{ matrix.service }}:latest
```

### 4.3 Understanding the Workflow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     WORKFLOW BREAKDOWN                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  on:                       ← WHEN does this workflow run?                   │
│    push:                                                                     │
│      branches: [main]      ← Only on pushes to main branch                  │
│      paths:                ← Only when these files change                   │
│        - '**/*.java'       ← Any Java file in any directory                 │
│                                                                              │
│  jobs:                     ← WHAT tasks to perform                          │
│    build:                  ← Job name (can have multiple jobs)              │
│      runs-on: ubuntu-latest ← Which machine to use                          │
│                                                                              │
│  services:                 ← Spin up containers alongside tests             │
│    postgres:               ← Database for integration tests                 │
│      options: --health-cmd ← Wait until service is ready                    │
│                                                                              │
│  steps:                    ← Individual actions within a job                │
│    - uses: actions/...     ← Pre-built actions from marketplace             │
│    - run: mvn ...          ← Shell commands                                 │
│                                                                              │
│  needs: build              ← This job depends on 'build' completing first   │
│                                                                              │
│  strategy.matrix           ← Run same steps for multiple items              │
│    service: [a, b, c]      ← Will run 3 parallel jobs                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


## 5. GitHub Actions Frontend Pipeline

### 5.1 Frontend CI Workflow

Create `.github/workflows/ci-frontend.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# PayFlow Frontend CI Pipeline
# Triggered on push/PR for React frontend changes
# ═══════════════════════════════════════════════════════════════════════════

name: Frontend CI

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'merchant-portal/**'
      - '.github/workflows/ci-frontend.yml'
  pull_request:
    branches: [ main ]
    paths:
      - 'merchant-portal/**'

env:
  NODE_VERSION: '18'

jobs:
  # ─────────────────────────────────────────────────────────────────────────
  # JOB 1: Lint and Type Check
  # ─────────────────────────────────────────────────────────────────────────
  lint:
    name: Lint & Type Check
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: merchant-portal

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: merchant-portal/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Run ESLint
        run: npm run lint

      - name: Run TypeScript check
        run: npm run type-check

  # ─────────────────────────────────────────────────────────────────────────
  # JOB 2: Unit Tests
  # ─────────────────────────────────────────────────────────────────────────
  test:
    name: Unit Tests
    runs-on: ubuntu-latest
    needs: lint
    defaults:
      run:
        working-directory: merchant-portal

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: merchant-portal/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Run Tests
        run: npm run test -- --coverage --watchAll=false

      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: frontend-coverage
          path: merchant-portal/coverage/

  # ─────────────────────────────────────────────────────────────────────────
  # JOB 3: Build
  # ─────────────────────────────────────────────────────────────────────────
  build:
    name: Build
    runs-on: ubuntu-latest
    needs: test
    defaults:
      run:
        working-directory: merchant-portal

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: merchant-portal/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Build application
        run: npm run build
        env:
          VITE_API_URL: ${{ vars.API_URL || 'http://localhost:8080' }}

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: frontend-build
          path: merchant-portal/dist/

  # ─────────────────────────────────────────────────────────────────────────
  # JOB 4: E2E Tests (Optional - runs on main only)
  # ─────────────────────────────────────────────────────────────────────────
  e2e:
    name: E2E Tests
    runs-on: ubuntu-latest
    needs: build
    if: github.ref == 'refs/heads/main'
    defaults:
      run:
        working-directory: merchant-portal

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: merchant-portal/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright
        run: npx playwright install --with-deps

      - name: Run E2E Tests
        run: npm run test:e2e
        continue-on-error: true  # Don't fail pipeline for E2E

      - name: Upload E2E Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: e2e-results
          path: merchant-portal/playwright-report/
```


### 5.2 Frontend Pipeline Visualization

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FRONTEND CI PIPELINE                                     │
│                                                                              │
│  Push to merchant-portal/**                                                 │
│         │                                                                    │
│         ▼                                                                    │
│  ┌──────────────┐                                                           │
│  │    Lint      │  ← ESLint + TypeScript checks                             │
│  │  & Type Check│                                                           │
│  └──────┬───────┘                                                           │
│         │                                                                    │
│         ▼                                                                    │
│  ┌──────────────┐                                                           │
│  │  Unit Tests  │  ← Jest + React Testing Library                           │
│  │  + Coverage  │                                                           │
│  └──────┬───────┘                                                           │
│         │                                                                    │
│         ▼                                                                    │
│  ┌──────────────┐                                                           │
│  │    Build     │  ← Vite production build                                  │
│  │  Application │                                                           │
│  └──────┬───────┘                                                           │
│         │                                                                    │
│         ▼ (main branch only)                                                │
│  ┌──────────────┐                                                           │
│  │  E2E Tests   │  ← Playwright browser tests                               │
│  │  (Optional)  │                                                           │
│  └──────────────┘                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Verification

### 6.1 Test Docker Build

```bash
# Build all images locally
docker-compose build

# Should see output like:
# Building service-registry
# Step 1/12 : FROM maven:3.9-eclipse-temurin-17 AS builder
# ...
# Successfully built abc123
# Successfully tagged payflow_service-registry:latest
```

### 6.2 Start the Stack

```bash
# Start infrastructure first
docker-compose up -d postgres redis localstack

# Wait for them to be healthy
docker-compose ps
# Should show: healthy for postgres and redis

# Start Spring Cloud services
docker-compose up -d service-registry config-server

# Wait 30 seconds for Eureka to start
sleep 30

# Start application services
docker-compose up -d api-gateway identity-service merchant-service

# Check all services
docker-compose ps
```

### 6.3 Verify Services

```bash
# Check Eureka Dashboard
curl http://localhost:8761/actuator/health
# Expected: {"status":"UP"}

# Check Config Server
curl http://localhost:8888/actuator/health
# Expected: {"status":"UP"}

# Check API Gateway
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Check Identity Service through Gateway
curl http://localhost:8080/api/v1/auth/health
# Expected: {"status":"UP","service":"identity-service"}

# Check Merchant Service through Gateway
curl http://localhost:8080/api/v1/merchants/health
# Expected: {"status":"UP","service":"merchant-service"}
```

### 6.4 Test Full Flow

```bash
# 1. Register a new user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!@#",
    "firstName": "Test",
    "lastName": "User"
  }'

# 2. Login to get tokens
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!@#"
  }'
# Save the accessToken from response

# 3. Create merchant (with token)
curl -X POST http://localhost:8080/api/v1/merchants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-access-token>" \
  -d '{
    "businessName": "Test Store",
    "businessType": "RETAIL",
    "email": "merchant@example.com"
  }'
```


### 6.5 Cleanup

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v

# Remove built images
docker-compose down --rmi local
```

---

## 7. File Structure After Part 06

```
payflow-payment-gateway/
├── .github/
│   └── workflows/
│       ├── ci-backend.yml          ← Backend CI pipeline
│       └── ci-frontend.yml         ← Frontend CI pipeline
│
├── docker/
│   ├── init-db.sql                 ← Database initialization
│   └── init-localstack.sh          ← AWS resources setup
│
├── docker-compose.yml              ← Full stack orchestration
│
├── service-registry/
│   └── Dockerfile                  ← Eureka server container
│
├── config-server/
│   └── Dockerfile                  ← Config server container
│
├── api-gateway/
│   └── Dockerfile                  ← API Gateway container
│
├── identity-service/
│   └── Dockerfile                  ← Identity service container
│
├── merchant-service/
│   └── Dockerfile                  ← Merchant service container
│
└── merchant-portal/
    └── Dockerfile                  ← (Created in Part 05)
```

---

## 8. Key Takeaways

### 8.1 Docker Concepts

| Concept | Purpose | Example |
|---------|---------|---------|
| **Multi-stage Build** | Smaller final images | Build with Maven, run with JRE |
| **Health Checks** | Container readiness | `wget --spider /actuator/health` |
| **Non-root User** | Security best practice | `USER spring` |
| **Volumes** | Persistent data | `postgres_data:/var/lib/postgresql/data` |
| **Networks** | Service communication | `payflow-network` |
| **depends_on** | Startup order | Wait for dependencies |

### 8.2 Docker Compose Dependencies

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SERVICE STARTUP ORDER                                    │
│                                                                              │
│  Level 0 (Infrastructure):                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                                  │
│  │ postgres │  │  redis   │  │localstack│                                  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                                  │
│       │             │             │                                          │
│       └─────────────┼─────────────┘                                          │
│                     │                                                        │
│  Level 1 (Discovery):                                                        │
│                     ▼                                                        │
│            ┌────────────────┐                                                │
│            │service-registry│                                                │
│            └───────┬────────┘                                                │
│                    │                                                         │
│  Level 2 (Config):                                                           │
│                    ▼                                                         │
│            ┌────────────────┐                                                │
│            │ config-server  │                                                │
│            └───────┬────────┘                                                │
│                    │                                                         │
│  Level 3 (Apps):   ▼                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                                  │
│  │api-gateway│  │identity- │  │merchant- │                                  │
│  │           │  │ service  │  │ service  │                                  │
│  └───────────┘  └──────────┘  └──────────┘                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 CI/CD Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CI/CD BEST PRACTICES                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ✅ DO:                                                                      │
│  • Use path filters to avoid unnecessary builds                             │
│  • Cache dependencies (Maven, npm) for faster builds                        │
│  • Run tests in isolated service containers                                 │
│  • Use health checks to ensure services are ready                           │
│  • Upload artifacts for debugging failed builds                             │
│  • Use matrix builds for multiple services/versions                         │
│                                                                              │
│  ❌ DON'T:                                                                   │
│  • Commit secrets to workflow files                                         │
│  • Skip tests to save time                                                  │
│  • Build on every branch (use branch filters)                               │
│  • Ignore failing tests with continue-on-error                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 9. Sprint 1 Complete Summary

🎉 **Congratulations!** You've completed Sprint 1 of the PayFlow Payment Gateway project!

### What You Built

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SPRINT 1 DELIVERABLES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  INFRASTRUCTURE                                                              │
│  ├── Service Registry (Eureka)      - Service discovery                     │
│  ├── Config Server                  - Centralized configuration             │
│  ├── PostgreSQL                     - Primary database                      │
│  ├── Redis                          - Caching & rate limiting               │
│  └── LocalStack                     - AWS services emulation                │
│                                                                              │
│  BACKEND SERVICES                                                            │
│  ├── API Gateway                    - Single entry point, routing           │
│  │   ├── Rate limiting (Redis)                                              │
│  │   ├── Correlation ID tracking                                            │
│  │   └── JWT authentication filter                                          │
│  │                                                                          │
│  ├── Identity Service               - Authentication & authorization        │
│  │   ├── User registration & login                                          │
│  │   ├── JWT token generation                                               │
│  │   ├── Refresh token rotation                                             │
│  │   └── Password encryption (BCrypt)                                       │
│  │                                                                          │
│  └── Merchant Service               - Merchant management                   │
│      ├── Merchant CRUD operations                                           │
│      ├── Business verification workflow                                     │
│      └── Multi-tenancy support                                              │
│                                                                              │
│  FRONTEND                                                                    │
│  └── Merchant Portal (React)        - Web dashboard                         │
│      ├── Login/Registration pages                                           │
│      ├── Protected routes                                                   │
│      ├── API integration (Axios)                                            │
│      └── Tailwind CSS styling                                               │
│                                                                              │
│  DEVOPS                                                                      │
│  ├── Docker Compose                 - Full stack orchestration              │
│  ├── GitHub Actions (Backend)       - Build, test, analyze                  │
│  └── GitHub Actions (Frontend)      - Lint, test, build                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                          ┌──────────────────┐                               │
│                          │  Merchant Portal │                               │
│                          │    (React)       │                               │
│                          └────────┬─────────┘                               │
│                                   │                                          │
│                                   │ HTTP                                     │
│                                   ▼                                          │
│                          ┌──────────────────┐                               │
│                          │   API Gateway    │                               │
│                          │     :8080        │                               │
│                          └────────┬─────────┘                               │
│                                   │                                          │
│                    ┌──────────────┼──────────────┐                          │
│                    │              │              │                          │
│                    ▼              ▼              ▼                          │
│           ┌──────────────┐ ┌──────────┐ ┌──────────────┐                   │
│           │   Identity   │ │ Merchant │ │   (Future)   │                   │
│           │   Service    │ │ Service  │ │   Services   │                   │
│           │    :8081     │ │  :8082   │ │              │                   │
│           └──────┬───────┘ └────┬─────┘ └──────────────┘                   │
│                  │              │                                           │
│                  └──────┬───────┘                                           │
│                         │                                                    │
│                         ▼                                                    │
│                  ┌──────────────┐                                           │
│                  │  PostgreSQL  │                                           │
│                  │    :5432     │                                           │
│                  └──────────────┘                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Skills You Learned

| Category | Skills |
|----------|--------|
| **Spring Cloud** | Eureka, Config Server, Gateway |
| **Security** | JWT, BCrypt, CORS, Rate Limiting |
| **Database** | JPA, PostgreSQL, Schema Design |
| **Frontend** | React, TypeScript, Tailwind, Axios |
| **DevOps** | Docker, Compose, GitHub Actions |
| **Architecture** | Microservices, API Gateway Pattern |

---

## 10. Next Steps

### Sprint 2: API Key Management

In the next sprint, you'll implement:

1. **API Key Generation**
   - Create secure API keys for merchants
   - Implement key rotation mechanism
   - Store keys in DynamoDB

2. **API Key Authentication**
   - Gateway filter for API key validation
   - Rate limiting per API key
   - Key permission scopes

3. **Developer Dashboard**
   - View and manage API keys
   - Usage statistics
   - Key regeneration UI

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SPRINT 2 PREVIEW                                         │
│                                                                              │
│  ┌──────────────┐         ┌──────────────┐         ┌──────────────┐        │
│  │   Merchant   │  ────▶  │  API Gateway │  ────▶  │  DynamoDB    │        │
│  │   Request    │         │  (Validate)  │         │  (API Keys)  │        │
│  │  + API Key   │         │              │         │              │        │
│  └──────────────┘         └──────────────┘         └──────────────┘        │
│                                                                              │
│  New Endpoints:                                                              │
│  • POST   /api/v1/keys              - Generate new API key                  │
│  • GET    /api/v1/keys              - List merchant's keys                  │
│  • DELETE /api/v1/keys/{keyId}      - Revoke API key                        │
│  • POST   /api/v1/keys/{keyId}/rotate - Rotate key                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Prepare for Sprint 2

```bash
# Make sure your Sprint 1 setup is running
docker-compose up -d

# Verify all services are healthy
docker-compose ps

# Check Eureka dashboard
open http://localhost:8761

# You're ready for Sprint 2!
```

---

## Q&A / Troubleshooting

### Common Issues

**Q: Docker build fails with "COPY failed: file not found"**
```bash
# Make sure you're running from project root
cd payflow-payment-gateway
docker-compose build
```

**Q: Service can't connect to Eureka**
```bash
# Check if service-registry is running
docker-compose logs service-registry

# Verify network connectivity
docker network inspect payflow-payment-gateway_payflow-network
```

**Q: Database connection refused**
```bash
# Check postgres health
docker-compose logs postgres

# Verify port isn't in use
netstat -an | grep 5432
```

**Q: GitHub Actions failing**
```bash
# Check workflow syntax
# Go to Actions tab in GitHub
# Click on failed run
# Expand failed step for details
```

**Q: Config Server not finding properties**
```bash
# Verify config files in config-repo
ls config-repo/

# Check Config Server can read them
curl http://localhost:8888/api-gateway/default
```

---

**Sprint 1 Complete! 🚀**

Move on to: [Sprint 2: API Key Management](../../sprint-02-api-key-management/requirements.md)
