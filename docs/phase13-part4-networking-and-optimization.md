# Phase 13 Part 4 — Docker Networking & Optimization

## Goal
- Configure isolated Docker networks (frontend, backend, data)
- Add health checks to all application containers
- Optimize images with layer caching and resource limits

## Key Concept

```
┌──────────────────────────────────────────────────────────┐
│  Network Segmentation                                    │
│                                                          │
│  frontend-net            backend-net           data-net  │
│  ┌───────────┐          ┌──────────────┐    ┌────────┐  │
│  │ portal    │◄────────►│ api-gateway  │◄──►│postgres│  │
│  │ checkout  │          │ identity-svc │◄──►│ redis  │  │
│  └───────────┘          │ payment-svc  │◄──►│ kafka  │  │
│                         └──────────────┘    └────────┘  │
│                                                          │
│  Portal cannot talk to Postgres directly                 │
│  Only api-gateway bridges frontend↔backend               │
└──────────────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 13 Part 3 completed (docker-compose working)

## Step-by-Step

### 1. Define Networks in docker-compose.yml

```yaml
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
  data:
    driver: bridge
    internal: true  # No external access
```

### 2. Assign Services to Networks

```yaml
services:
  postgres:
    networks:
      - data

  redis:
    networks:
      - data
      - backend  # Services need Redis for caching

  kafka:
    networks:
      - data
      - backend

  identity-service:
    networks:
      - backend
      - data

  payment-service:
    networks:
      - backend
      - data

  api-gateway:
    networks:
      - frontend  # Accessible from frontends
      - backend   # Routes to backend services

  merchant-portal:
    networks:
      - frontend

  hosted-checkout:
    networks:
      - frontend
```

### 3. Add Health Checks to Java Services

```yaml
  identity-service:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  payment-service:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  api-gateway:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
```

### 4. Resource Limits

```yaml
  payment-service:
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '1.0'
        reservations:
          memory: 256M
          cpus: '0.5'

  postgres:
    deploy:
      resources:
        limits:
          memory: 256M
        reservations:
          memory: 128M

  redis:
    deploy:
      resources:
        limits:
          memory: 128M
```

### 5. Java Dockerfile Optimization (JVM flags)

```dockerfile
# In identity-service/Dockerfile and payment-service/Dockerfile
# Add JVM memory settings for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 6. Docker Build Cache Optimization

```dockerfile
# In Java Dockerfiles — separate dependency download from code copy
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first (cached unless pom changes)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
RUN mvn dependency:go-offline -B

# Then copy source (changes frequently)
COPY src ./src
RUN mvn package -DskipTests
```

### 7. Startup Order Script (`scripts/wait-for-it.sh`)

```bash
#!/bin/bash
# Usage: ./wait-for-it.sh host:port -- command
host="$1"
shift
port="$1"
shift

echo "Waiting for $host:$port..."
while ! nc -z "$host" "$port" 2>/dev/null; do
  sleep 1
done
echo "$host:$port is available"
exec "$@"
```

## Verification

```bash
# Start with network isolation
docker compose up -d

# Verify network isolation
docker compose exec merchant-portal ping -c 1 postgres
# Should FAIL — portal can't reach DB

docker compose exec api-gateway ping -c 1 postgres
# Should FAIL — gateway on backend net, not data net

docker compose exec payment-service ping -c 1 postgres
# Should SUCCEED — payment on data net

# Check health status
docker compose ps
# All services should show "(healthy)"

# Check resource usage
docker stats --no-stream
# Memory should be within defined limits
```

## Git Commit

```bash
git add docker-compose.yml scripts/wait-for-it.sh
git commit -m "build(docker): add network isolation, health checks, and resource limits"
```

## Next Step
→ **Phase 14 Part 2** — GitHub Actions backend CI pipeline
