# Sprint 0, Part 03: Docker Infrastructure

**Duration:** 2-3 hours  
**Prerequisites:** Docker Desktop installed, Part 01-02 completed

---

## 1. What We're Building

In this part, you'll create **Docker Compose** configuration for local infrastructure:
- **PostgreSQL** — Relational database (4 schemas)
- **Redis** — Caching and rate limiting
- **DynamoDB Local** — AWS DynamoDB emulator (webhook events, routing metrics)
- **LocalStack** — AWS services simulator (SQS, SNS)

---

## 2. Concepts Deep Dive

### Docker Compose Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Docker Compose Components                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   docker-compose-infra.yml                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ version: "3.8"                                                   │       │
│   │                                                                  │       │
│   │ services:           ◄── Containers to run                       │       │
│   │   postgres:              Port 5432                               │       │
│   │   redis:                 Port 6379                               │       │
│   │   dynamodb-local:        Port 8000                               │       │
│   │   localstack:            Port 4566                               │       │
│   │                                                                  │       │
│   │ volumes:            ◄── Persistent data storage                 │       │
│   │   postgres_data:                                                 │       │
│   │                                                                  │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why These Services?

| Service | Purpose in PayFlow | Port | Why This Choice |
|---------|-------------------|------|-----------------|
| **PostgreSQL** | Store users, merchants, orders, payments | 5432 | Reliable, ACID compliant, free |
| **Redis** | Cache, rate limiting, idempotency keys | 6379 | Fast (in-memory), versatile |
| **DynamoDB Local** | Webhook events, audit trail, routing metrics | 8000 | AWS DynamoDB emulator |
| **LocalStack** | SQS queues, SNS topics | 4566 | AWS simulator for messaging |

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Infrastructure Components                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│   │   PostgreSQL    │     │     Redis       │     │ DynamoDB Local  │       │
│   │   Port: 5432    │     │   Port: 6379    │     │   Port: 8000    │       │
│   │                 │     │                 │     │                 │       │
│   │ Schemas:        │     │ Used for:       │     │ Tables:         │       │
│   │ • identity      │     │ • JWT blacklist │     │ • webhook_events│       │
│   │ • merchant      │     │ • Rate limiting │     │ • audit_trail   │       │
│   │ • payment       │     │ • Idempotency   │     │ • routing_metric│       │
│   │ • settlement    │     │ • Caching       │     │                 │       │
│   └─────────────────┘     └─────────────────┘     └─────────────────┘       │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                       LocalStack                                 │       │
│   │                       Port: 4566                                 │       │
│   │                                                                  │       │
│   │   SQS Queues:                    SNS Topics:                    │       │
│   │   • payflow-payment-events       • payflow-email-notifications  │       │
│   │   • payflow-webhook-delivery     • payflow-sms-notifications    │       │
│   │   • payflow-notification                                         │       │
│   │   • payflow-payment-events-dlq                                   │       │
│   │   • payflow-webhook-delivery-dlq                                 │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Verify Docker is running:

```powershell
docker --version
# Expected: Docker version 24.x.x

docker compose version
# Expected: Docker Compose version v2.x.x

# Make sure Docker Desktop is running (check system tray)
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create docker-compose-infra.yml

Create `docker-compose-infra.yml` in the project root:

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

**Line-by-Line Explanation:**

| Section | What It Does |
|---------|--------------|
| `image: postgres:15` | Use PostgreSQL 15 (stable, production-ready) |
| `container_name` | Fixed name for easy identification |
| `ports: "5432:5432"` | Map host port to container port |
| `POSTGRES_PASSWORD: payflow_secret` | Database password (use in application.yml) |
| `volumes: postgres_data` | Persist data across container restarts |
| `healthcheck` | Docker checks if service is healthy |
| `command: redis-server --maxmemory 128mb` | Limit Redis memory, evict LRU keys |
| `dynamodb-local -inMemory` | DynamoDB data in memory (faster for dev) |
| `SERVICES: sqs,sns` | Only enable SQS and SNS in LocalStack |

---

### Step 4.2: Create docker/init-db.sql

Create the `docker` folder and `init-db.sql`:

```powershell
mkdir docker
```

Create `docker/init-db.sql`:

```sql
-- This script runs automatically when PostgreSQL container starts for the first time.
-- It creates the separate schemas for each service.

-- Create schemas (one per service)
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS settlement;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA identity TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA merchant TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA payment TO payflow;
GRANT ALL PRIVILEGES ON SCHEMA settlement TO payflow;
```

**Why Schemas?**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Why Separate Schemas?                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Single Database, Multiple Schemas (What we're doing):                     │
│                                                                              │
│   payflow (database)                                                        │
│   ├── identity (schema)                                                     │
│   │   └── users table                                                       │
│   ├── merchant (schema)                                                     │
│   │   ├── merchants table                                                   │
│   │   └── api_keys table                                                    │
│   ├── payment (schema)                                                      │
│   │   ├── orders table                                                      │
│   │   └── payments table                                                    │
│   └── settlement (schema)                                                   │
│       └── settlements table                                                 │
│                                                                              │
│   Benefits:                                                                  │
│   • Each service accesses only its schema                                   │
│   • Clear ownership of tables                                               │
│   • Easy to split into separate databases later                             │
│   • Simpler local development (one PostgreSQL container)                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### Step 4.3: Create docker/init-localstack.sh

Create `docker/init-localstack.sh`:

```bash
#!/bin/bash
# This script runs when LocalStack starts.
# It creates the SQS queues and SNS topics we need.

echo "Creating SQS queues..."

# Payment events queue (consumed by webhook-service)
awslocal sqs create-queue --queue-name payflow-payment-events

# Webhook delivery queue (retry queue for webhook-service)
awslocal sqs create-queue --queue-name payflow-webhook-delivery

# Notification queue (consumed by notification-service)
awslocal sqs create-queue --queue-name payflow-notification

# Dead letter queues
awslocal sqs create-queue --queue-name payflow-payment-events-dlq
awslocal sqs create-queue --queue-name payflow-webhook-delivery-dlq

echo "Creating SNS topics..."

# Email notifications
awslocal sns create-topic --name payflow-email-notifications

# SMS notifications
awslocal sns create-topic --name payflow-sms-notifications

echo "LocalStack initialization complete!"
echo "SQS endpoint: http://localhost:4566"
echo "SNS endpoint: http://localhost:4566"
```

**Queue Naming Convention:**

All PayFlow resources use the `payflow-` prefix for easy identification:

| Queue/Topic | Purpose |
|-------------|---------|
| `payflow-payment-events` | Payment state change events |
| `payflow-webhook-delivery` | Webhook delivery jobs |
| `payflow-notification` | Email/SMS notification jobs |
| `payflow-*-dlq` | Dead letter queues for failed messages |

---

## 5. Verification

### Start the Containers

```powershell
# Navigate to project root
cd C:\path\to\payflow-payment-gateway

# Start all infrastructure containers
docker compose -f docker-compose-infra.yml up -d

# Watch the logs (Ctrl+C to exit)
docker compose -f docker-compose-infra.yml logs -f
```

### Verify Containers Running

```powershell
docker ps
```

**Expected Output:**
```
CONTAINER ID   IMAGE                        PORTS                    NAMES
abc123...      postgres:15                  0.0.0.0:5432->5432/tcp   payflow-postgres
def456...      redis:7-alpine               0.0.0.0:6379->6379/tcp   payflow-redis
ghi789...      amazon/dynamodb-local        0.0.0.0:8000->8000/tcp   payflow-dynamodb
jkl012...      localstack/localstack        0.0.0.0:4566->4566/tcp   payflow-localstack
```

### Test PostgreSQL

```powershell
# Connect to PostgreSQL and list schemas
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dn"
```

**Expected Output:**
```
   List of schemas
    Name    |  Owner  
------------+---------
 identity   | payflow
 merchant   | payflow
 payment    | payflow
 public     | pg_database_owner
 settlement | payflow
(5 rows)
```

### Test Redis

```powershell
docker exec -it payflow-redis redis-cli ping
```

**Expected Output:**
```
PONG
```

### Test DynamoDB Local

```powershell
# List tables (should be empty initially)
docker exec -it payflow-dynamodb aws dynamodb list-tables --endpoint-url http://localhost:8000
```

**Expected Output:**
```json
{
    "TableNames": []
}
```

### Test LocalStack

```powershell
# List SQS queues
docker exec -it payflow-localstack awslocal sqs list-queues
```

**Expected Output:**
```json
{
    "QueueUrls": [
        "http://sqs.ap-south-1.localhost.localstack.cloud:4566/000000000000/payflow-payment-events",
        "http://sqs.ap-south-1.localhost.localstack.cloud:4566/000000000000/payflow-webhook-delivery",
        "http://sqs.ap-south-1.localhost.localstack.cloud:4566/000000000000/payflow-notification",
        "http://sqs.ap-south-1.localhost.localstack.cloud:4566/000000000000/payflow-payment-events-dlq",
        "http://sqs.ap-south-1.localhost.localstack.cloud:4566/000000000000/payflow-webhook-delivery-dlq"
    ]
}
```

---

## 6. File Structure After This Part

```
payflow-payment-gateway/
├── pom.xml
├── docker-compose-infra.yml        # Infrastructure compose file
├── docker/
│   ├── init-db.sql                 # PostgreSQL init script
│   └── init-localstack.sh          # LocalStack init script
└── common-lib/
    └── ...
```

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **docker-compose-infra.yml** | Defines all infrastructure containers |
| **volumes** | `postgres_data` persists data across restarts |
| **healthcheck** | Docker monitors container health |
| **init scripts** | Run once on first container start |
| **payflow-** prefix | All our AWS resources use this prefix |
| **DynamoDB Local** | Separate container for DynamoDB emulation |

---

## 8. Useful Commands

```powershell
# Start containers
docker compose -f docker-compose-infra.yml up -d

# Stop containers (keep data)
docker compose -f docker-compose-infra.yml stop

# Stop and remove containers (keep data)
docker compose -f docker-compose-infra.yml down

# Stop, remove, AND delete data (fresh start)
docker compose -f docker-compose-infra.yml down -v

# View logs
docker compose -f docker-compose-infra.yml logs -f

# View specific service logs
docker compose -f docker-compose-infra.yml logs -f postgres

# Restart specific service
docker compose -f docker-compose-infra.yml restart postgres

# Check container health
docker ps
```

---

## 9. Q&A / Troubleshooting

### Q: Container won't start - "port already in use"

**A:** Something else is using that port. Find and stop it:
```powershell
netstat -ano | findstr :5432
taskkill /PID <pid> /F
```

### Q: PostgreSQL schemas not created

**A:** The init script only runs on first container start. To re-run:
```powershell
docker compose -f docker-compose-infra.yml down -v
docker compose -f docker-compose-infra.yml up -d
```

### Q: LocalStack queues not created

**A:** Check if init script has execute permissions or run manually:
```powershell
docker exec -it payflow-localstack /etc/localstack/init/ready.d/init.sh
```

### Q: How do I connect from my IDE?

**A:** Use these connection settings:

| Service | Host | Port | Credentials |
|---------|------|------|-------------|
| PostgreSQL | localhost | 5432 | payflow / payflow_secret |
| Redis | localhost | 6379 | (no auth) |
| DynamoDB | localhost | 8000 | (any credentials) |
| LocalStack | localhost | 4566 | (any credentials) |

---

## 10. Related Concepts

| Topic | What to Learn Next | When Needed |
|-------|-------------------|-------------|
| Docker networking | Container-to-container communication | Phase 13 |
| Docker health checks | Production readiness | Phase 13 |
| LocalStack Pro | More AWS services | If needed |
| AWS SDK | Connecting to SQS/SNS | Sprint 6+ |

---

## 11. Next Steps

**Continue to:** [part-04-git-workflow.md](./part-04-git-workflow.md)

In the next part, you'll set up Git and project documentation.
