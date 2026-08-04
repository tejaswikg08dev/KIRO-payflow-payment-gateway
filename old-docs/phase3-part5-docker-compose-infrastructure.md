# Phase 3 — Part 5: Docker Compose Infrastructure

> In this part, we set up Docker Compose to run all infrastructure locally:
> PostgreSQL, Redis, DynamoDB Local, and LocalStack (SQS/SNS).
> After this: one command starts all databases and messaging services.

---

## 1. What Is Docker Compose? (Concept)

Docker Compose lets you define and run multiple Docker containers with ONE command.

```
WITHOUT Docker Compose:
docker run -d --name postgres -e POSTGRES_PASSWORD=secret -p 5432:5432 postgres:15
docker run -d --name redis -p 6379:6379 redis:7
docker run -d --name dynamodb -p 8000:8000 amazon/dynamodb-local
docker run -d --name localstack -p 4566:4566 localstack/localstack
→ 4 separate commands to remember, type, and manage 😩

WITH Docker Compose:
docker compose -f docker-compose-infra.yml up -d
→ ONE command starts everything ✅
→ ONE command stops everything: docker compose -f docker-compose-infra.yml down
```

---

## 2. Our Infrastructure Components

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| PostgreSQL | postgres:15 | 5432 | Relational database (users, payments, merchants) |
| Redis | redis:7-alpine | 6379 | Cache, rate limiting, idempotency keys |
| DynamoDB Local | amazon/dynamodb-local | 8000 | Webhook events, routing metrics, audit |
| LocalStack | localstack/localstack | 4566 | Simulates SQS queues + SNS topics locally |

---

## 3. File: docker-compose-infra.yml

This file is already created at the project root. Here's what each section means:

### 3.1 PostgreSQL

```yaml
postgres:
  image: postgres:15                        # Official PostgreSQL 15 image
  container_name: payflow-postgres          # Friendly name in Docker
  ports:
    - "5432:5432"                           # Host:Container port mapping
  environment:
    POSTGRES_DB: payflow                    # Create database named "payflow"
    POSTGRES_USER: payflow                  # Username
    POSTGRES_PASSWORD: payflow_secret       # Password
  volumes:
    - postgres_data:/var/lib/postgresql/data           # Persist data between restarts
    - ./docker/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql  # Run init script
```

**The init-db.sql** runs automatically on FIRST start:
- Creates schemas: `identity`, `merchant`, `payment`, `settlement`
- These schemas separate each service's data logically

### 3.2 Redis

```yaml
redis:
  image: redis:7-alpine                    # Lightweight Redis 7
  container_name: payflow-redis
  ports:
    - "6379:6379"
  command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
```

**Why `--maxmemory 128mb --maxmemory-policy allkeys-lru`?**
- Limits Redis to 128 MB RAM (don't want it eating all laptop memory)
- When full, `allkeys-lru` evicts least-recently-used keys (safe for cache)

### 3.3 DynamoDB Local

```yaml
dynamodb-local:
  image: amazon/dynamodb-local:latest
  container_name: payflow-dynamodb
  ports:
    - "8000:8000"
  command: "-jar DynamoDBLocal.jar -sharedDb -inMemory"
```

**What is DynamoDB Local?**
- Official Amazon tool that runs DynamoDB on your laptop
- Behaves exactly like real DynamoDB (same APIs)
- `-inMemory` = data lost on restart (fine for development)
- `-sharedDb` = all tables in one database file

### 3.4 LocalStack (SQS + SNS Simulator)

```yaml
localstack:
  image: localstack/localstack:latest
  container_name: payflow-localstack
  ports:
    - "4566:4566"
  environment:
    SERVICES: sqs,sns                       # Only start SQS and SNS (not all 50 services)
    DEFAULT_REGION: ap-south-1              # Mumbai region (same as our AWS deployment)
  volumes:
    - ./docker/init-localstack.sh:/etc/localstack/init/ready.d/init.sh
```

**What is LocalStack?**
- Simulates AWS services on your laptop
- Same API as real AWS — your code doesn't know the difference
- The init script creates our SQS queues and SNS topics automatically

---

## 4. File: docker/init-db.sql

```sql
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS settlement;
```

This runs once when PostgreSQL container starts for the first time.
After that, Flyway (in each service) creates the actual tables.

---

## 5. File: docker/init-localstack.sh

```bash
#!/bin/bash
# Creates SQS queues and SNS topics when LocalStack starts

awslocal sqs create-queue --queue-name payflow-payment-events
awslocal sqs create-queue --queue-name payflow-webhook-delivery
awslocal sqs create-queue --queue-name payflow-notification
awslocal sqs create-queue --queue-name payflow-payment-events-dlq
awslocal sqs create-queue --queue-name payflow-webhook-delivery-dlq

awslocal sns create-topic --name payflow-email-notifications
awslocal sns create-topic --name payflow-sms-notifications
```

---

## 6. How to Run

### Step 1: Make sure Docker Desktop is running

### Step 2: Start all infrastructure
```cmd
cd payflow-payment-gateway
docker compose -f docker-compose-infra.yml up -d
```

**Expected output:**
```
[+] Running 4/4
 ✔ Container payflow-postgres    Started
 ✔ Container payflow-redis       Started
 ✔ Container payflow-dynamodb    Started
 ✔ Container payflow-localstack  Started
```

### Step 3: Verify each service

**PostgreSQL:**
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dn"
```
Expected: Shows `identity`, `merchant`, `payment`, `settlement` schemas.

**Redis:**
```cmd
docker exec -it payflow-redis redis-cli ping
```
Expected: `PONG`

**DynamoDB Local:**
```cmd
curl http://localhost:8000
```
Expected: `{"__type":"com.amazonaws.dynamodb.v20120810#MissingAuthenticationTokenException"...}`
(This error is actually correct — it means DynamoDB is responding!)

**LocalStack SQS:**
```cmd
aws --endpoint-url=http://localhost:4566 sqs list-queues --region ap-south-1
```
Expected: List of 5 queues we created.

### Step 4: Stop everything
```cmd
docker compose -f docker-compose-infra.yml down
```

### Step 5: Stop AND delete data (fresh start)
```cmd
docker compose -f docker-compose-infra.yml down -v
```
(`-v` removes volumes — PostgreSQL data is wiped)

---

## 7. Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "port 5432 already in use" | Local PostgreSQL running | Stop local PostgreSQL or change port |
| "port 6379 already in use" | Local Redis running | Stop local Redis or change port |
| Container exits immediately | Check logs | `docker logs payflow-postgres` |
| init-db.sql not running | Already ran before (data exists) | Run `docker compose down -v` then `up -d` |
| LocalStack queues missing | init script didn't run | Check `docker logs payflow-localstack` |

---

## 8. Phase 3 Complete! 🎉

All infrastructure services are now ready:

| Component | Status | How to Start |
|-----------|--------|-------------|
| PostgreSQL | ✅ Docker | `docker compose -f docker-compose-infra.yml up -d` |
| Redis | ✅ Docker | (same command) |
| DynamoDB Local | ✅ Docker | (same command) |
| SQS/SNS (LocalStack) | ✅ Docker | (same command) |
| Eureka (Service Registry) | ✅ Java | `cd service-registry && mvn spring-boot:run` |
| Config Server | ✅ Java | `cd config-server && mvn spring-boot:run` |
| API Gateway | ✅ Java | `cd api-gateway && mvn spring-boot:run` |
| Common Library | ✅ JAR | Compiled with `mvn clean install` |

**Full local startup order:**
```
1. docker compose -f docker-compose-infra.yml up -d    (databases)
2. cd service-registry && mvn spring-boot:run           (Eureka)
3. cd config-server && mvn spring-boot:run              (Config)
4. cd api-gateway && mvn spring-boot:run                (Gateway)
5. (Then start business services in Phase 4+)
```

---

## 9. Full-Stack Docker Compose (Service Discovery & Config in Docker)

> So far we've been running `docker-compose-infra.yml` (databases only) and starting Java services manually.
> Now we have a FULL `docker-compose.yml` that runs EVERYTHING — databases, service-registry, config-server,
> application services, and even the frontend. One command. Zero manual steps.

---

### 9.1 Two Compose Files: What's the Difference?

| File | What It Runs | When to Use |
|------|-------------|-------------|
| `docker-compose-infra.yml` | PostgreSQL, Redis, DynamoDB Local, LocalStack | When developing Java services locally (you run services via Maven) |
| `docker-compose.yml` | **Everything** — infra + Java services + frontends | Full stack integration testing, demos, CI/CD pipelines |

```
docker-compose-infra.yml (databases only):
┌──────────────────────────────────────────┐
│  PostgreSQL │ Redis │ DynamoDB │ LocalStack│
└──────────────────────────────────────────┘

docker-compose.yml (FULL STACK):
┌──────────────────────────────────────────────────────────────────┐
│  PostgreSQL │ Redis │ Kafka │ Zookeeper     ← Infrastructure     │
│  service-registry │ config-server            ← Discovery & Config │
│  identity-service │ payment-service          ← Business Services  │
│  api-gateway │ bank-simulator                ← Gateway & Sim      │
│  merchant-portal │ hosted-checkout           ← Frontend Apps      │
└──────────────────────────────────────────────────────────────────┘
```

---

### 9.2 New Infrastructure Services: service-registry & config-server

These two containers are the glue that holds the microservices together in Docker mode.

#### service-registry (Eureka Server)

```yaml
  service-registry:
    build:
      context: .                                    # Build from project root (multi-module)
      dockerfile: service-registry/Dockerfile       # Module-specific Dockerfile
    container_name: payflow-registry                # Friendly name in `docker ps`
    ports:
      - "8761:8761"                                 # Eureka dashboard: http://localhost:8761
    environment:
      SERVER_PORT: 8761                             # Tell Spring Boot which port to use
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
                                                    # ↑ Docker pings this every interval
      interval: 10s                                 # Check every 10 seconds
      timeout: 5s                                   # Give up if no response in 5s
      retries: 5                                    # Mark unhealthy after 5 failures
      start_period: 40s                             # Wait 40s before first check (JVM startup!)
    deploy:
      resources:
        limits:
          memory: 512M                              # Max RAM this container can use
        reservations:
          memory: 256M                              # Guaranteed minimum RAM
    networks:
      - backend-net                                 # Only needs backend network
```

**Why `start_period: 40s`?**
Java/Spring Boot apps take 20-40 seconds to start. Without `start_period`, Docker would start health-checking immediately and mark the container "unhealthy" before it even finishes booting. The 40s grace period says: "Don't judge me for the first 40 seconds — I'm still waking up."

#### config-server

```yaml
  config-server:
    build:
      context: .                                    # Same pattern: build from root
      dockerfile: config-server/Dockerfile          # Config Server Dockerfile
    container_name: payflow-config                  # Shows as "payflow-config" in Docker
    ports:
      - "8888:8888"                                 # Config endpoint: http://localhost:8888
    environment:
      SERVER_PORT: 8888
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://service-registry:8761/eureka/
                                                    # ↑ THIS is the key Docker override!
                                                    # In dev: http://localhost:8761/eureka/
                                                    # In Docker: http://service-registry:8761/eureka/
    depends_on:
      service-registry:
        condition: service_healthy                  # WAIT until Eureka is healthy!
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 40s
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
    networks:
      - backend-net
```

**Why does config-server need `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`?**
Config Server registers itself with Eureka so other services can discover it. In development, Eureka runs at `localhost:8761`. But inside Docker, there's no "localhost" that points to Eureka — you must use the container name `service-registry` as the hostname.

---

### 9.3 Startup Ordering Chain

This is one of the trickiest parts of Docker Compose with microservices. Services must start in the RIGHT ORDER — you can't register with Eureka if Eureka isn't running yet!

```
┌─────────────────────┐
│  service-registry   │  ← Starts FIRST (no dependencies)
│  (Eureka)           │
└─────────┬───────────┘
          │ condition: service_healthy
          ▼
┌─────────────────────┐
│  config-server      │  ← Starts SECOND (needs Eureka)
│  (Central Config)   │
└─────────┬───────────┘
          │ condition: service_healthy
          ▼
┌─────────────────────────────────────────────────────┐
│  identity-service │ payment-service │ api-gateway   │  ← Start THIRD
│  (Business logic services need both Eureka + Config)│
└─────────────────────────────────────────────────────┘
          │ condition: service_healthy
          ▼
┌─────────────────────────────────────────┐
│  merchant-portal │ hosted-checkout      │  ← Start LAST (need Gateway)
│  (Frontend apps need API Gateway ready) │
└─────────────────────────────────────────┘
```

#### `condition: service_healthy` vs `condition: service_started`

| Condition | What It Means | When to Use |
|-----------|---------------|-------------|
| `service_started` | "The container process began" | Non-critical deps (bank-simulator) |
| `service_healthy` | "The health check passed" | Critical deps (registry, config, databases) |

**Why does this matter?**

With `service_started`, Docker just checks that the container's process is running. But a Java app can take 30+ seconds to actually be READY to accept connections. If config-server starts immediately after service-registry's process begins (but before Eureka is actually listening), config-server will crash with "Connection refused".

`service_healthy` means: "Wait until the health check passes — meaning the application is truly READY to serve requests."

---

### 9.4 Docker Networking Explained

Our `docker-compose.yml` defines THREE networks to isolate traffic:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        HOST MACHINE                                   │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │  frontend-net (bridge)                                         │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐  │   │
│  │  │ merchant-   │  │ hosted-      │  │ api-gateway         │  │   │
│  │  │ portal :3000│  │ checkout:3001│  │ :8080               │  │   │
│  │  └─────────────┘  └──────────────┘  └──────────┬──────────┘  │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                     │                 │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │  backend-net (bridge)                            │             │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┴──────────┐ │   │
│  │  │ service-     │  │ config-      │  │ api-gateway         │ │   │
│  │  │ registry     │  │ server       │  │ (also on frontend)  │ │   │
│  │  │ :8761        │  │ :8888        │  └─────────────────────┘ │   │
│  │  └──────────────┘  └──────────────┘                           │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐ │   │
│  │  │ identity-    │  │ payment-     │  │ bank-simulator      │ │   │
│  │  │ service:8081 │  │ service:8082 │  │ :9090               │ │   │
│  │  └──────┬───────┘  └──────┬───────┘  └─────────────────────┘ │   │
│  └───────────────────────────────────────────────────────────────┘   │
│            │                  │                                        │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  data-net (bridge, internal: true) ← NO EXTERNAL ACCESS     │     │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │     │
│  │  │ postgres     │  │ redis        │  │ kafka + zookeeper │  │     │
│  │  │ :5432        │  │ :6379        │  │ :9092             │  │     │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘  │     │
│  └─────────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────────┘
```

#### Why Three Networks?

| Network | Purpose | Who's On It | External Access? |
|---------|---------|-------------|-----------------|
| `frontend-net` | Browser-facing traffic | merchant-portal, hosted-checkout, api-gateway | ✅ Yes (ports 3000, 3001, 8080) |
| `backend-net` | Service-to-service communication | All Java services, Redis | ✅ Yes (for debugging, Eureka dashboard) |
| `data-net` | Database access only | PostgreSQL, Redis, Kafka, identity/payment services | ❌ **No** (`internal: true`) |

#### What Does `internal: true` Mean?

```yaml
networks:
  data-net:
    driver: bridge
    internal: true  # ← THIS
```

This means containers on `data-net` **cannot reach the internet** and **cannot be reached from outside Docker**. Even though PostgreSQL maps port 5432, the `internal: true` flag on the network prevents external routing. This is a security layer:
- Your databases can't accidentally phone home
- External attackers can't directly reach databases even if ports are exposed
- Only services that are ALSO on `data-net` (like identity-service) can talk to PostgreSQL

#### Which Services Are On Which Networks?

| Service | frontend-net | backend-net | data-net | Why? |
|---------|:---:|:---:|:---:|------|
| merchant-portal | ✅ | | | Only serves browser traffic |
| hosted-checkout | ✅ | | | Only serves browser traffic |
| api-gateway | ✅ | ✅ | | Bridge between frontend and backend |
| service-registry | | ✅ | | Internal service discovery |
| config-server | | ✅ | | Internal configuration |
| identity-service | | ✅ | ✅ | Needs backend (Eureka) + data (PostgreSQL) |
| payment-service | | ✅ | ✅ | Needs backend (Eureka) + data (PostgreSQL, Kafka) |
| bank-simulator | | ✅ | | Mock bank API, no database |
| postgres | | | ✅ | Database only — maximum isolation |
| redis | | ✅ | ✅ | Cache (backend) + session store (data) |
| kafka | | ✅ | ✅ | Event bus for services + data persistence |
| zookeeper | | | ✅ | Kafka internal coordination only |

---

### 9.5 Container Hostname vs Localhost

**This is the #1 source of confusion when moving from local dev to Docker.**

In local development, everything runs on your machine:
```
identity-service → http://localhost:8761/eureka/     ← Eureka on your machine
identity-service → jdbc:postgresql://localhost:5432  ← PostgreSQL on your machine
```

In Docker, each service runs in its OWN isolated container. `localhost` inside a container means "myself" — not the host machine, not other containers:
```
identity-service → http://localhost:8761/eureka/     ← ❌ WRONG! Points to itself!
identity-service → http://service-registry:8761/eureka/  ← ✅ Uses container name
```

**The rule:** In Docker, replace `localhost` with the container name (which acts as a hostname on the Docker network).

| Context | Eureka URL | PostgreSQL URL |
|---------|-----------|----------------|
| Local dev (Maven) | `http://localhost:8761/eureka/` | `jdbc:postgresql://localhost:5432/payflow` |
| Docker Compose | `http://service-registry:8761/eureka/` | `jdbc:postgresql://postgres:5432/payflow` |

**How does Docker resolve `service-registry` to an IP?** Docker's built-in DNS server automatically resolves container names to their internal IP addresses on the shared network. It's like having a private DNS that knows all your containers.

---

### 9.6 Environment Variable Overrides

Each service in `docker-compose.yml` has environment variables that override the defaults in `application.yml`. This is how the same Java code works both locally and in Docker — without changing a single line of code.

#### Spring Boot's "Relaxed Binding"

Spring Boot maps environment variables to YAML properties using a simple formula:

```
YAML property:          eureka.client.service-url.defaultZone
                             ↓ (dots → underscores, uppercase, hyphens → underscores)
Environment variable:   EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
```

So setting `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/` in Docker Compose overrides whatever's in your `application.yml` file. Spring Boot sees it and says "Oh, the user wants a different Eureka URL? Done."

#### Complete Environment Variables Per Service

**service-registry:**
```yaml
environment:
  SERVER_PORT: 8761                    # → server.port=8761
```

**config-server:**
```yaml
environment:
  SERVER_PORT: 8888                    # → server.port=8888
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://service-registry:8761/eureka/
                                       # → eureka.client.service-url.defaultZone
```

**identity-service:**
```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker       # → spring.profiles.active=docker
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payflow
                                       # → spring.datasource.url (uses container name!)
  SPRING_DATASOURCE_USERNAME: payflow  # → spring.datasource.username
  SPRING_DATASOURCE_PASSWORD: payflow_secret  # → spring.datasource.password
  SPRING_DATA_REDIS_HOST: redis        # → spring.data.redis.host (container name!)
  SPRING_DATA_REDIS_PORT: 6379         # → spring.data.redis.port
  SERVER_PORT: 8081                    # → server.port
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://service-registry:8761/eureka/
```

**payment-service:**
```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payflow
  SPRING_DATASOURCE_USERNAME: payflow
  SPRING_DATASOURCE_PASSWORD: payflow_secret
  SPRING_DATA_REDIS_HOST: redis
  SPRING_DATA_REDIS_PORT: 6379
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092  # → spring.kafka.bootstrap-servers
  BANK_SIMULATOR_HOST: bank-simulator          # Custom property for bank connection
  BANK_SIMULATOR_PORT: 9090
  SERVER_PORT: 8082
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://service-registry:8761/eureka/
```

**api-gateway:**
```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  SPRING_DATA_REDIS_HOST: redis
  SPRING_DATA_REDIS_PORT: 6379
  IDENTITY_SERVICE_URL: http://identity-service:8081   # For JWT validation
  PAYMENT_SERVICE_URL: http://payment-service:8082     # For direct routing
  SERVER_PORT: 8080
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://service-registry:8761/eureka/
```

#### What Does `SPRING_PROFILES_ACTIVE: docker` Do?

This activates the `docker` Spring profile. If you had a file called `application-docker.yml` in your service, Spring Boot would load it ON TOP of the default `application.yml`. This lets you have Docker-specific settings without touching the default config. In our case, the environment variables handle most overrides, but the profile serves as a signal that we're running in Docker mode.

---

### 9.7 Resource Limits Explained

```yaml
deploy:
  resources:
    limits:
      memory: 512M      # Maximum RAM this container can use
    reservations:
      memory: 256M      # Minimum guaranteed RAM
```

#### Why Set Memory Limits?

Without limits, a single Java service could eat ALL your laptop's RAM:
- JVM default heap = 25% of total system RAM
- 8 Java services × 2 GB each = 16 GB consumed! 😱
- Your laptop has 16 GB total → everything freezes

With `memory: 512M`, each container is CAPPED at 512 MB. If it tries to use more:

```
Container exceeds 512M → Docker OOM Killer activates → Container is killed and restarted
```

**OOM = Out Of Memory.** Docker's OOM Killer forcefully stops the container. You'll see this in `docker ps` as the container restarting repeatedly.

#### How JVM Respects Container Limits

Our Dockerfiles include `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`:
- `UseContainerSupport` tells the JVM: "Hey, you're in a container — check cgroup limits, not total host RAM"
- `MaxRAMPercentage=75.0` means: "Use at most 75% of the container's memory limit for heap"
- So with a 512 MB limit: heap max = 512 × 0.75 = 384 MB (leaves 128 MB for JVM metaspace, threads, etc.)

#### `limits` vs `reservations`

| Setting | Meaning | Analogy |
|---------|---------|---------|
| `limits: memory: 512M` | "You will NEVER get more than 512M" | Hard ceiling |
| `reservations: memory: 256M` | "You're guaranteed AT LEAST 256M" | Reserved seat |

Docker uses reservations for scheduling — it won't place more containers on a host than it can guarantee memory for. In local development this mostly doesn't matter, but in production (Kubernetes/ECS) it's critical.

---

### 9.8 Health Check Deep Dive

Every critical service defines a health check. Here's what each parameter means:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 40s
```

| Parameter | Value | Meaning |
|-----------|-------|---------|
| `test` | `curl -f .../actuator/health` | The command Docker runs to check health. `-f` makes curl fail (exit 1) on HTTP errors |
| `interval` | `10s` | Run the health check every 10 seconds |
| `timeout` | `5s` | If the check doesn't respond in 5s, count it as failed |
| `retries` | `5` | After 5 consecutive failures, mark as "unhealthy" |
| `start_period` | `40s` | Grace period — failures during this time don't count toward retries |

#### Timeline: How a Health Check Works

```
T=0s    Container starts
T=0-40s [start_period] Health checks run but failures DON'T count
T=40s   Grace period ends — now failures count!
T=50s   Check #1: curl returns 200 → HEALTHY ✅
        (Container stays healthy as long as checks pass)

        ... later ...

T=200s  Check #20: curl returns 503 → failure 1/5
T=210s  Check #21: curl returns 503 → failure 2/5
T=220s  Check #22: curl returns 503 → failure 3/5
T=230s  Check #23: curl returns 503 → failure 4/5
T=240s  Check #24: curl returns 503 → failure 5/5 → UNHEALTHY ❌
```

#### How `depends_on` Uses Health Status

```yaml
config-server:
  depends_on:
    service-registry:
      condition: service_healthy    # Wait for HEALTHY status
```

Docker Compose watches the health status of dependencies:
1. `service-registry` container starts
2. Docker runs health checks during `start_period` (failures ignored)
3. After 40s, a health check passes → status = `healthy`
4. Docker Compose sees `service_healthy` condition met
5. NOW `config-server` container starts

**What if the health check never passes?** The dependency stays "unhealthy" and dependent services NEVER start. You'll see them stuck in "waiting" state. Check logs: `docker compose logs service-registry`

---

### 9.9 How to Run the Full Stack

#### Start Everything

```cmd
docker compose up -d
```

That's it. One command. Docker Compose:
1. Builds all Java service images (first time only — uses cache after)
2. Creates networks (frontend-net, backend-net, data-net)
3. Starts services in dependency order
4. Waits for health checks between stages

#### Watch Startup in Real-Time

```cmd
docker compose logs -f
```

You'll see the startup chain unfold:
```
payflow-registry  | Started ServiceRegistryApplication in 28.4s
payflow-config    | Started ConfigServerApplication in 22.1s
payflow-identity  | Started IdentityServiceApplication in 18.3s
payflow-payment   | Started PaymentServiceApplication in 19.7s
payflow-gateway   | Started ApiGatewayApplication in 12.5s
payflow-portal    | nginx: ready
payflow-checkout  | nginx: ready
```

#### Verify Each Layer

**1. Check Eureka Dashboard (service-registry):**
Open http://localhost:8761 in your browser. You should see all services registered:
- CONFIG-SERVER
- IDENTITY-SERVICE
- PAYMENT-SERVICE
- API-GATEWAY

**2. Check Config Server:**
```cmd
curl http://localhost:8888/identity-service/default
```
Should return JSON with identity-service's configuration properties.

**3. Check API Gateway Health:**
```cmd
curl http://localhost:8080/actuator/health
```
Should return `{"status":"UP"}` with details about all downstream services.

**4. Check Frontend Apps:**
- Merchant Portal: http://localhost:3000
- Hosted Checkout: http://localhost:3001

#### Stop Everything

```cmd
docker compose down
```
Stops all containers but keeps volumes (database data persists).

#### Stop and Wipe All Data (Fresh Start)

```cmd
docker compose down -v
```
The `-v` flag removes volumes — PostgreSQL data, Redis cache, everything. Next `up` starts fresh.

---

### 9.10 Common Issues with Full-Stack Docker

| Issue | Symptom | Cause | Fix |
|-------|---------|-------|-----|
| service-registry takes too long | config-server never starts | JVM needs more startup time | Increase `start_period` to 60s |
| config-server can't reach Eureka | "Connection refused" in logs | Wrong network or hostname | Verify both are on `backend-net`, check `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` |
| Application services crash on startup | Repeated restarts | Started before config-server ready | Ensure `depends_on: config-server: condition: service_healthy` |
| "Port already in use" | Container won't start | Local service using same port | Stop local service or change port mapping |
| OOM Killed (exit code 137) | Container keeps restarting | Exceeding memory limit | Increase `memory` limit or reduce JVM heap |
| Build fails | "COPY failed" during image build | Missing compiled JAR | Run `mvn clean package -DskipTests` first |
| Frontend can't reach API | Network errors in browser | Gateway not healthy yet | Wait for gateway health check, check `frontend-net` |

**Pro tip:** When things go wrong, always check logs first:
```cmd
docker compose logs service-registry    # Specific service
docker compose logs -f                  # All services, following
docker compose ps                       # See status of all containers
```

---

## 10. Interview Notes

**Q: "How do you run locally during development?"**
> "Docker Compose starts PostgreSQL, Redis, DynamoDB Local, and LocalStack (SQS/SNS) with one command. Java services run via Maven. Everything works on localhost without any cloud dependency."

**Q: "How do you simulate AWS services locally?"**
> "LocalStack simulates SQS and SNS. DynamoDB Local simulates DynamoDB. All use the same AWS SDK APIs — my code is identical between local and production. Only the endpoint URL changes."

---

## Next Step

→ Move to **Phase 4: Identity Service**
→ Start with **`phase4-part1-project-setup-and-database.md`**

In Phase 4, we build the first real business service — user registration, login, and JWT authentication.
