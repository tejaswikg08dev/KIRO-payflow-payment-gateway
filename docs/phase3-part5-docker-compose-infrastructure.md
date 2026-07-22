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

## 9. Interview Notes

**Q: "How do you run locally during development?"**
> "Docker Compose starts PostgreSQL, Redis, DynamoDB Local, and LocalStack (SQS/SNS) with one command. Java services run via Maven. Everything works on localhost without any cloud dependency."

**Q: "How do you simulate AWS services locally?"**
> "LocalStack simulates SQS and SNS. DynamoDB Local simulates DynamoDB. All use the same AWS SDK APIs — my code is identical between local and production. Only the endpoint URL changes."

---

## Next Step

→ Move to **Phase 4: Identity Service**
→ Start with **`phase4-part1-project-setup-and-database.md`**

In Phase 4, we build the first real business service — user registration, login, and JWT authentication.
