# Sprint 0: Foundation — Design

**Sprint Duration:** 1 week  
**Sprint Goal:** Development environment ready, project initialized

---

## High-Level Design

### Project Structure

```
payflow-payment-gateway/
├── pom.xml                      ← Parent POM (dependency management)
├── README.md                    ← Project documentation
├── .gitignore                   ← Git ignore rules
├── docker-compose-infra.yml     ← Infrastructure containers
├── docker/
│   ├── init-db.sql              ← PostgreSQL schema initialization
│   └── init-localstack.sh       ← AWS LocalStack initialization
└── common-lib/
    ├── pom.xml                  ← Module POM
    └── src/main/java/com/payflow/common/
        ├── dto/
        │   ├── ApiResponse.java
        │   └── ErrorDetail.java
        ├── constant/
        │   ├── PaymentStatus.java
        │   └── PaymentMethod.java
        ├── exception/
        │   ├── PayflowException.java
        │   └── GlobalExceptionHandler.java
        └── util/
            └── IdGenerator.java
```

---

## Infrastructure Design

### Docker Compose Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Local Development Infrastructure                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   docker-compose-infra.yml                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                                                                  │       │
│   │   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐       │       │
│   │   │  PostgreSQL  │   │    Redis     │   │  LocalStack  │       │       │
│   │   │   :5432      │   │    :6379     │   │  :4566/:8000 │       │       │
│   │   │              │   │              │   │              │       │       │
│   │   │ 4 schemas:   │   │ • Caching    │   │ • SQS        │       │       │
│   │   │ • identity   │   │ • Sessions   │   │ • SNS        │       │       │
│   │   │ • merchant   │   │ • Rate limit │   │ • DynamoDB   │       │       │
│   │   │ • payment    │   │              │   │              │       │       │
│   │   │ • settlement │   │              │   │              │       │       │
│   │   └──────────────┘   └──────────────┘   └──────────────┘       │       │
│   │                                                                  │       │
│   │   Volume: payflow-postgres-data (persistent)                    │       │
│   │   Volume: payflow-redis-data (persistent)                       │       │
│   │   Network: payflow-network (bridge)                             │       │
│   │                                                                  │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema Design

### PostgreSQL Schemas

```sql
-- Four isolated schemas in single PostgreSQL instance
CREATE SCHEMA IF NOT EXISTS identity;    -- Users, roles
CREATE SCHEMA IF NOT EXISTS merchant;    -- Merchants, API keys
CREATE SCHEMA IF NOT EXISTS payment;     -- Orders, payments, refunds
CREATE SCHEMA IF NOT EXISTS settlement;  -- Settlements, payouts
```

---

## Common Library Design

### Package Structure

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `dto` | Data Transfer Objects | ApiResponse, ErrorDetail, PagedResponse |
| `constant` | Enums and Constants | PaymentStatus, PaymentMethod |
| `exception` | Custom Exceptions | PayflowException, GlobalExceptionHandler |
| `util` | Utility Classes | IdGenerator |

### ApiResponse Pattern

```java
// Standard API response wrapper
{
    "success": true,
    "data": { ... },
    "error": null,
    "timestamp": "2026-08-04T12:00:00Z"
}

// Error response
{
    "success": false,
    "data": null,
    "error": {
        "code": "VALIDATION_ERROR",
        "message": "Invalid input",
        "details": ["Field 'amount' must be positive"]
    },
    "timestamp": "2026-08-04T12:00:00Z"
}
```

---

## Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Java Version | 17 LTS | Long-term support, modern features |
| Spring Boot | 3.2.x | Latest stable with virtual threads |
| Build Tool | Maven | Standard for enterprise Java |
| Container | Docker | Industry standard, easy local dev |
| Database | PostgreSQL 15 | Reliable, JSON support, free |
| Cache | Redis 7 | Fast, versatile, free tier |

---

## Files to Create

| File | Purpose | Sprint Part |
|------|---------|-------------|
| `pom.xml` | Parent POM | Part 01 |
| `common-lib/pom.xml` | Module POM | Part 01 |
| `docker-compose-infra.yml` | Infrastructure | Part 02 |
| `docker/init-db.sql` | DB schemas | Part 02 |
| `common-lib/src/**/*.java` | Shared code | Part 03 |
| `.gitignore` | Git config | Part 04 |
| `README.md` | Documentation | Part 04 |

---

**Next:** [tasks.md](./tasks.md)
