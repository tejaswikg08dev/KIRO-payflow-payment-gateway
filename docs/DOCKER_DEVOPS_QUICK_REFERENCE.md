# PayFlow — Docker & DevOps Quick Reference

**For Interview Preparation — Everything You Need to Know**

---

## 📋 Service Ports Reference (MEMORIZE THIS!)

| Service | Port | Container Name |
|---------|------|----------------|
| **service-registry** | 8761 | payflow-registry |
| **config-server** | 8888 | payflow-config |
| **api-gateway** | 8080 | payflow-gateway |
| **identity-service** | 8081 | payflow-identity |
| **merchant-service** | 8082 | payflow-merchant |
| **payment-service** | 8083 | payflow-payment |
| **routing-service** | 8084 | payflow-routing |
| **settlement-service** | 8085 | payflow-settlement |
| **webhook-service** | 8086 | payflow-webhook |
| **notification-service** | 8087 | payflow-notification |
| **bank-simulator** | 9000 | payflow-bank-sim |
| **frontend-dashboard** | 3000 | payflow-dashboard |
| **frontend-checkout** | 3001 | payflow-checkout |

### Infrastructure Services
| Service | Port | Container Name |
|---------|------|----------------|
| PostgreSQL | 5432 | payflow-postgres |
| Redis | 6379 | payflow-redis |
| DynamoDB Local | 8000 | payflow-dynamodb |
| LocalStack (SQS/SNS) | 4566 | payflow-localstack |
| Zookeeper | 2181 | payflow-zookeeper |
| Kafka | 9092 | payflow-kafka |

---

## 🐳 Docker Compose Files — When to Use Each

| File | Purpose | When to Use |
|------|---------|-------------|
| `docker-compose-infra.yml` | Infrastructure only (DB, Redis, etc.) | **Sprint 1 local dev** — run Java services in IDE |
| `docker-compose.yml` | Full stack (all services + Kafka) | **Sprint 2+** — full integration testing |
| `docker-compose.prod.yml` | Production (connects to AWS RDS/ElastiCache) | **Production deployment** |

### Sprint 1 Development Workflow
```bash
# 1. Start infrastructure only
docker compose -f docker-compose-infra.yml up -d

# 2. Verify services are healthy
docker compose -f docker-compose-infra.yml ps

# 3. Run Java services in your IDE (or mvn spring-boot:run)
# Order: service-registry → config-server → identity-service → merchant-service → api-gateway

# 4. Run frontend with npm
cd frontend-dashboard && npm run dev
```

### Full Stack Testing (Sprint 2+)
```bash
# Build all images
docker compose build

# Start everything
docker compose up -d

# Watch logs
docker compose logs -f

# Check health
docker compose ps

# Stop and cleanup
docker compose down -v
```

---

## 🏗️ Dockerfile Deep Dive

### Multi-Stage Build Pattern

```dockerfile
# Stage 1: BUILD (Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy POM files first (dependency caching!)
COPY pom.xml ./pom.xml
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY service-name/pom.xml ./service-name/pom.xml

# Download dependencies (CACHED if pom.xml unchanged)
RUN mvn dependency:go-offline -pl service-name -am -B

# Copy source and build
COPY service-name/src ./service-name/src
RUN mvn package -pl service-name -am -DskipTests -B

# Stage 2: RUNTIME (JRE only — smaller image)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security: Run as non-root user
RUN addgroup -S payflow && adduser -S payflow -G payflow

# Copy JAR from build stage
COPY --from=build /app/service-name/target/*.jar app.jar
RUN chown payflow:payflow app.jar
USER payflow

# JVM container optimization
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Why Multi-Stage Build?
| Benefit | Explanation |
|---------|-------------|
| **Smaller image** | Final image has JRE only (~200MB vs ~800MB) |
| **Faster deploys** | Smaller images transfer faster |
| **Better security** | No build tools = less attack surface |
| **Cached layers** | Dependencies cached if POM unchanged |

### Key JVM Options for Containers
| Option | Purpose |
|--------|---------|
| `-XX:+UseContainerSupport` | JVM respects cgroup memory limits |
| `-XX:MaxRAMPercentage=75.0` | Use 75% of container memory for heap |
| `-XX:+UseG1GC` | G1 garbage collector (good for containers) |
| `-Djava.security.egd=file:/dev/./urandom` | Faster random number generation |

---

## 🔀 Docker Networking

```
┌─────────────────────────────────────────────────────────────────┐
│                    NETWORK SEGMENTATION                          │
│                                                                  │
│   data-net (internal: true) — No external access                │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐              │
│   │  postgres   │ │   redis     │ │   kafka     │              │
│   └─────────────┘ └─────────────┘ └─────────────┘              │
│         ▲               ▲               ▲                       │
│         └───────────────┼───────────────┘                       │
│                         │                                       │
│   backend-net — Service-to-service communication                │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐              │
│   │  identity   │ │  payment    │ │  api-gateway│              │
│   └─────────────┘ └─────────────┘ └─────────────┘              │
│                                            │                    │
│   frontend-net — External access           │                    │
│   ┌─────────────┐ ┌─────────────┐         │                    │
│   │  dashboard  │ │  checkout   │ ← ──────┘                    │
│   └─────────────┘ └─────────────┘                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Why Network Segmentation?
- **data-net (internal: true)**: Databases can't be accessed from outside Docker
- **backend-net**: Services communicate with each other
- **frontend-net**: Only gateway exposed to frontends

---

## 🚀 CI/CD Pipeline (GitHub Actions)

### Pipeline Flow
```
┌────────────────────────────────────────────────────────────────┐
│  Developer pushes code                                          │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Job 1: build-and-test                                   │  │
│  │  • Checkout code                                         │  │
│  │  • Setup JDK 17                                          │  │
│  │  • Start PostgreSQL + Redis containers                   │  │
│  │  • Build common-lib                                      │  │
│  │  • Compile all services                                  │  │
│  │  • Run unit tests                                        │  │
│  │  • Run integration tests                                 │  │
│  │  • Generate JaCoCo coverage                              │  │
│  └───────────────────────┬─────────────────────────────────┘  │
│                          │                                      │
│                          ▼ (only if build passes)              │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Job 2: docker-build (main branch only)                  │  │
│  │  • Matrix strategy: build 7 services in parallel         │  │
│  │  • Push to GitHub Container Registry (GHCR)              │  │
│  └───────────────────────┬─────────────────────────────────┘  │
│                          │                                      │
│                          ▼                                      │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Job 3: security-scan                                    │  │
│  │  • Trivy vulnerability scanner                           │  │
│  │  • Upload results to GitHub Security tab                 │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### Key CI/CD Concepts

| Concept | Explanation |
|---------|-------------|
| **Path filters** | CI only runs when relevant files change (saves minutes) |
| **Service containers** | PostgreSQL/Redis spin up alongside job for tests |
| **Matrix strategy** | Build multiple services in parallel |
| **needs:** | Job dependencies (docker-build waits for build-and-test) |
| **GHCR** | GitHub Container Registry (free with GitHub) |
| **Trivy** | Container vulnerability scanner |

### GitHub Actions Key Syntax
```yaml
# Triggers
on:
  push:
    branches: [main, develop]
    paths:
      - 'identity-service/**'    # Only run if these files change
  pull_request:
    branches: [main]

# Service containers (databases for tests)
services:
  postgres:
    image: postgres:15-alpine
    env:
      POSTGRES_DB: test
    options: >-
      --health-cmd pg_isready
      --health-interval 10s

# Matrix strategy (parallel builds)
strategy:
  matrix:
    service: [identity, payment, gateway]

# Job dependencies
jobs:
  build:
    runs-on: ubuntu-latest
  docker:
    needs: build    # Waits for build job
    if: github.ref == 'refs/heads/main'  # Only on main branch
```

---

## ☁️ AWS Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     AWS PRODUCTION SETUP                         │
│                                                                  │
│  VPC (10.0.0.0/16)                                              │
│  │                                                               │
│  ├── Public Subnets (10.0.1.0/24, 10.0.2.0/24)                 │
│  │   ├── Application Load Balancer (ALB)                        │
│  │   ├── NAT Gateway (for private subnet internet access)       │
│  │   └── Bastion Host (SSH access)                              │
│  │                                                               │
│  ├── Private Subnets (10.0.11.0/24, 10.0.12.0/24)              │
│  │   └── ECS Fargate Tasks (containerized services)             │
│  │                                                               │
│  └── Database Subnets (10.0.21.0/24, 10.0.22.0/24)             │
│      ├── RDS PostgreSQL (Multi-AZ)                              │
│      └── ElastiCache Redis                                      │
│                                                                  │
│  Other AWS Services:                                            │
│  ├── ECR — Docker image registry                                │
│  ├── S3 + CloudFront — Frontend hosting                         │
│  ├── SQS — Message queues                                       │
│  ├── SNS — Email/SMS notifications                              │
│  ├── DynamoDB — Webhook events, audit trail                     │
│  └── CloudWatch — Logs, metrics, alarms                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### AWS Free Tier Services Used
| Service | Free Tier | PayFlow Usage |
|---------|-----------|---------------|
| EC2 t3.micro | 750 hrs/month | Backend services |
| RDS db.t3.micro | 750 hrs/month | PostgreSQL |
| ElastiCache cache.t3.micro | 750 hrs/month | Redis |
| S3 | 5GB | Frontend hosting |
| CloudFront | 1TB/month | CDN |
| DynamoDB | 25GB + 25 RCU/WCU | Always free |
| SQS | 1M requests/month | Always free |
| SNS | 1M publishes/month | Always free |

### ECS Fargate Key Concepts
| Concept | Explanation |
|---------|-------------|
| **Cluster** | Logical grouping of services |
| **Service** | Maintains desired count of tasks |
| **Task** | Running container instance |
| **Task Definition** | Blueprint (image, CPU, memory, env vars) |
| **Fargate** | Serverless — no EC2 management |

---

## 🔧 Common Docker Commands

```bash
# Build single service
docker build -t payflow/identity-service -f identity-service/Dockerfile .

# Build all with compose
docker compose build

# Start infrastructure only
docker compose -f docker-compose-infra.yml up -d

# Start full stack
docker compose up -d

# View logs (follow)
docker compose logs -f identity-service

# Check container health
docker inspect payflow-identity --format='{{.State.Health.Status}}'

# Execute command in container
docker exec -it payflow-postgres psql -U payflow -d payflow

# Stop and remove volumes
docker compose down -v

# Prune unused resources
docker system prune -a
```

---

## 🎤 Interview Talking Points

### "Walk me through your Docker setup"
> "We use multi-stage builds — first stage compiles with Maven and JDK 17, second stage runs with JRE-only Alpine for a smaller image. We separate networks: data-net for databases with no external access, backend-net for service communication, and frontend-net for user-facing apps. In development, we run infrastructure in Docker with docker-compose-infra.yml while running Java services in the IDE for faster iteration."

### "How does your CI/CD pipeline work?"
> "GitHub Actions triggers on push to main/develop. First job runs build and tests with PostgreSQL and Redis service containers. If that passes, matrix strategy builds Docker images for all services in parallel and pushes to GitHub Container Registry. Then Trivy scans for vulnerabilities. For frontend, we deploy to S3 and invalidate CloudFront cache."

### "How do you handle secrets in Docker?"
> "Locally, environment variables in docker-compose.yml. In production, we use AWS Secrets Manager and reference secrets in ECS task definitions. JWT secrets and database passwords never go in Dockerfiles or git."

### "What's your container health check strategy?"
> "Every service has Spring Boot Actuator /actuator/health endpoint. Docker HEALTHCHECK curls this endpoint. start-period gives the JVM time to start up. Compose uses depends_on with condition: service_healthy to ensure startup order."

---

## ✅ Pre-Interview Checklist

- [ ] Can explain multi-stage Docker builds
- [ ] Know all service ports by heart
- [ ] Understand network segmentation
- [ ] Can describe CI/CD pipeline stages
- [ ] Know when to use each docker-compose file
- [ ] Understand ECS Fargate concepts
- [ ] Can explain JVM container flags
- [ ] Know AWS free tier limits

