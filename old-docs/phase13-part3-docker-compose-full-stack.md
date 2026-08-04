# Phase 13 Part 3 — Docker Compose Full Stack

## Goal
- Define all services in a single docker-compose file
- Configure infrastructure (Postgres, Redis, Kafka) and application services
- Enable one-command startup of the entire platform

## Key Concept

```
┌──────────────────────────────────────────────────────────┐
│  docker-compose.yml                                      │
│                                                          │
│  Infrastructure          Application         Frontend    │
│  ┌───────────┐          ┌──────────────┐    ┌────────┐  │
│  │ PostgreSQL │◄────────│identity-svc  │    │portal  │  │
│  │ Redis     │◄────────│payment-svc   │    │checkout│  │
│  │ Kafka     │◄────────│api-gateway   │    └────────┘  │
│  │ Zookeeper │          │bank-simulator│               │
│  └───────────┘          └──────────────┘               │
└──────────────────────────────────────────────────────────┘
```

## Prerequisites
- All Dockerfiles created (Java services + React apps)
- Docker Compose V2 installed

## Step-by-Step

### 1. Full docker-compose.yml (project root)

```yaml
version: '3.8'

services:
  # ─── Infrastructure ───────────────────────────
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: payflow
      POSTGRES_USER: payflow
      POSTGRES_PASSWORD: payflow_secret
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payflow"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

  # ─── Application Services ─────────────────────
  identity-service:
    build: ./identity-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payflow
      SPRING_DATASOURCE_USERNAME: payflow
      SPRING_DATASOURCE_PASSWORD: payflow_secret
      SPRING_REDIS_HOST: redis
      JWT_SECRET: super-secret-jwt-key-for-dev
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  payment-service:
    build: ./payment-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payflow
      SPRING_DATASOURCE_USERNAME: payflow
      SPRING_DATASOURCE_PASSWORD: payflow_secret
      SPRING_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      BANK_SIMULATOR_HOST: bank-simulator
      BANK_SIMULATOR_PORT: 9090
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy

  api-gateway:
    build: ./api-gateway
    ports:
      - "8080:8080"
    environment:
      IDENTITY_SERVICE_URL: http://identity-service:8081
      PAYMENT_SERVICE_URL: http://payment-service:8082
      SPRING_REDIS_HOST: redis
    depends_on:
      - identity-service
      - payment-service

  bank-simulator:
    build: ./bank-simulator
    ports:
      - "9090:9090"

  # ─── Frontend ─────────────────────────────────
  merchant-portal:
    build:
      context: ./merchant-portal
      args:
        VITE_API_URL: http://localhost:8080
    ports:
      - "3000:80"
    depends_on:
      - api-gateway

  hosted-checkout:
    build:
      context: ./hosted-checkout
      args:
        VITE_API_URL: http://localhost:8080
    ports:
      - "3001:80"
    depends_on:
      - api-gateway

volumes:
  postgres_data:
```

### 2. Init SQL Script (`scripts/init-db.sql`)

```sql
-- Create separate schemas for each service
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS payment;
```

### 3. Startup Commands

```bash
# Start everything
docker compose up -d

# Watch logs
docker compose logs -f payment-service

# Check status
docker compose ps

# Stop everything
docker compose down

# Full reset (including data)
docker compose down -v
```

## Verification

```bash
docker compose up -d
docker compose ps
# All services should show "running" or "healthy"

# Test API gateway
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# Test merchant portal
curl -I http://localhost:3000
# HTTP/1.1 200 OK

# Test hosted checkout
curl -I http://localhost:3001
# HTTP/1.1 200 OK

docker compose down
```

## Git Commit

```bash
git add docker-compose.yml scripts/init-db.sql
git commit -m "build(docker): add full-stack docker-compose with all services"
```

## Next Step
→ **Phase 13 Part 4** — Docker networking and optimization
