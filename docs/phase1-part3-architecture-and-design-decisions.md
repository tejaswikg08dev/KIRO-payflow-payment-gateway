# Phase 1 — Part 3: Architecture & Design Decisions

> This document explains WHY we made each architectural decision.
> In interviews, you'll be asked "Why did you choose X over Y?" — this prepares you.

---

## 1. Why Microservices (Not Monolith)?

### Decision: Microservices with separate service per domain capability

**For a payment system, microservices are the right choice because:**

| Concern | Monolith Problem | Microservices Solution |
|---------|-----------------|----------------------|
| **Payment processing** needs 99.99% uptime | If settlement code has a bug, payments go down too | Payment service is isolated — settlement bugs don't affect it |
| **Fraud detection** may need Python/ML later | Can't mix Java + Python in one app easily | Each service can use its own language |
| **Settlement** runs heavy batch jobs at midnight | Batch processing steals resources from payment API | Settlement has its own resources, scales separately |
| **Webhook delivery** has its own retry mechanism | Retry loops block other threads | Webhook service manages its own retry queue |
| **Team scaling** | 10 developers stepping on each other in one codebase | Each team owns one service |

### What We Lose with Microservices

| Challenge | How We Handle It |
|-----------|-----------------|
| Network calls between services add latency | OpenFeign + Eureka (fast discovery), circuit breakers |
| Distributed transactions are complex | Saga pattern (SQS-based), eventual consistency |
| More deployment complexity | Docker Compose locally, CI/CD automation |
| Debugging across services harder | Correlation IDs, structured logging, X-Ray tracing |
| Data consistency harder | Database-per-service + events for sync |

---

## 2. Database Per Service (Not Shared Database)

### Decision: Each service owns its data, no direct DB access across services

```
✅ CORRECT (what we do):
Payment Service → own PostgreSQL schema → exposes REST API
Settlement Service → calls Payment Service REST API to get captured payments

❌ WRONG (what we avoid):
Settlement Service → directly queries Payment Service's database tables
(This creates tight coupling — can't change Payment DB schema without breaking Settlement)
```

### How Services Share Data

| Pattern | When Used | Example |
|---------|-----------|---------|
| **REST API call** | Need data in real-time | Settlement asks Payment for captured payments |
| **SQS event** | Notify about something that happened | Payment publishes "payment.captured" event |
| **Shared library (DTOs)** | Common data structures | common-lib has PaymentDTO used by multiple services |

---

## 3. Synchronous vs Asynchronous Communication

### Decision: Use sync for critical path, async for everything else

```
SYNCHRONOUS (must wait for response):
├── Payment → Fraud Check   (need score to decide approve/decline)
├── Payment → Routing       (need bank response to tell customer)
└── Gateway → Identity      (need to validate JWT before routing)

ASYNCHRONOUS (fire-and-forget):
├── Payment → Webhook Service  (merchant can wait a few seconds)
├── Payment → Notification     (email can wait)
├── Payment → Settlement       (happens at end of day anyway)
└── Settlement → Webhook       (settlement notification not urgent)
```

### Why Not Make Everything Async?

Customer is WAITING on the checkout page. They need to know in 2-3 seconds:
"Did my payment work or not?" So the payment → routing → bank path MUST be synchronous.

---

## 4. API Gateway Pattern

### Decision: Single entry point (Spring Cloud Gateway) for all external traffic

```
WITHOUT GATEWAY:                      WITH GATEWAY:
Client → Identity Service (8081)      Client → Gateway (8080) → routes to correct service
Client → Payment Service (8083)       
Client → Merchant Service (8082)      Benefits:
                                      • Single URL for clients
Problems:                             • Rate limiting in one place
• Client needs to know all ports      • Auth validation in one place
• Rate limiting duplicated            • SSL termination once
• No central auth check              • Swagger aggregation
• No central logging                  • Can change backend without client changes
```

---

## 5. Service Discovery (Eureka)

### Decision: Services register with Eureka, find each other by name

```
WITHOUT DISCOVERY:
payment-service calls: http://192.168.1.5:8084/route  ← Hardcoded IP!
If routing-service moves to another server → everything breaks

WITH EUREKA:
payment-service calls: http://ROUTING-SERVICE/route   ← Service NAME
Eureka knows routing-service is at 192.168.1.5:8084 right now
If it moves → Eureka updates automatically, no config change needed
```

---

## 6. Centralized Configuration (Spring Cloud Config)

### Decision: All service configs in one place, services fetch on startup

```
WITHOUT CONFIG SERVER:
├── identity-service/src/resources/application.yml  ← DB URL here
├── payment-service/src/resources/application.yml   ← DB URL here
├── routing-service/src/resources/application.yml   ← DB URL here
└── Problem: Change DB password → edit 7 files, redeploy 7 services

WITH CONFIG SERVER:
├── config-server/configurations/identity-service.yml
├── config-server/configurations/payment-service.yml
├── config-server/configurations/routing-service.yml
└── Change DB password → edit once, services refresh automatically
```

---

## 7. Caching Strategy

### Decision: Multi-level caching (Local → Redis → Database)

```
REQUEST FLOW:
1. Check Caffeine (local in-memory cache)     → Hit? Return immediately (µs)
2. Check Redis (shared distributed cache)     → Hit? Return (1-2ms)
3. Query PostgreSQL (source of truth)         → Return (5-50ms), cache result

WHAT WE CACHE:
├── Idempotency keys → Redis (TTL: 24hrs)
├── Rate limit counters → Redis (TTL: 1 minute)
├── API key → merchant mapping → Redis (TTL: 1hr)
├── Routing rules → Caffeine local (TTL: 5min)
├── JWT blacklist → Redis (TTL: token expiry)
└── Merchant config → Redis (TTL: 1hr, invalidate on update)
```

---

## 8. Error Handling Strategy

### Decision: Standardized error response across all services

```json
{
  "error": {
    "code": "PAYMENT_FAILED",
    "message": "Payment was declined due to insufficient funds",
    "details": {
      "response_code": "51",
      "payment_id": "pay_abc123"
    },
    "timestamp": "2026-07-19T14:30:00Z",
    "trace_id": "req_xyz789"
  }
}
```

**Error code categories:**
| Prefix | Meaning | HTTP Status |
|--------|---------|-------------|
| AUTH_ | Authentication errors | 401 |
| PERM_ | Permission/authorization errors | 403 |
| NOT_FOUND_ | Resource not found | 404 |
| VALIDATION_ | Input validation failures | 400 |
| PAYMENT_ | Payment processing errors | 422 |
| RATE_LIMIT_ | Too many requests | 429 |
| INTERNAL_ | Server errors | 500 |
| GATEWAY_ | Upstream service errors | 502 |

---

## 9. Resilience Patterns

### Decision: Resilience4j for all inter-service communication

| Pattern | What It Does | Our Config |
|---------|-------------|-----------|
| **Circuit Breaker** | Stop calling a dead service | Open after 5 failures in 10s, half-open after 30s |
| **Retry** | Retry on transient failure | 3 attempts, 100ms/200ms/400ms delays |
| **Rate Limiter** | Protect service from overload | 100 req/s per API key |
| **Bulkhead** | Isolate thread pools | Payment gets 20 threads, webhook gets 10 |
| **Timeout** | Don't wait forever | 5s for bank calls, 2s for internal service calls |

```
Payment Service → calls Routing Service:
├── Timeout: 5 seconds
├── Retry: 2 attempts (only on 503/timeout, NOT on 400/404)
├── Circuit Breaker: Opens if 50% fail in last 10 seconds
│   ├── CLOSED (normal) → requests go through
│   ├── OPEN (broken) → fail immediately, don't even try
│   └── HALF-OPEN (testing) → allow 3 requests, see if it recovers
└── Fallback: Return "PAYMENT_FAILED: bank unavailable" to customer
```

---

## 10. Security Architecture Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| External auth | API Key (X-Api-Key header) | Simple for merchants, same as Stripe |
| Internal auth | JWT (service-to-service) | Stateless, fast validation |
| Password storage | BCrypt (strength 12) | Intentionally slow to prevent brute force |
| API secret storage | SHA-256 hash in DB | Can't reverse, validate by hashing input |
| Webhook signing | HMAC-SHA256 | Industry standard, Stripe uses this |
| Card data | Never stored, pass-through only | PCI-DSS requires it (even for simulation) |
| Rate limiting | Token bucket algorithm in Redis | Fair, burst-friendly, industry standard |

---

## 11. Design Patterns Used

| Pattern | Where Used | Purpose |
|---------|-----------|---------|
| **State Machine** | Payment Service | Enforce valid state transitions |
| **Strategy** | Routing Service | Pluggable routing algorithms |
| **Builder** | ISO 8583 messages | Construct complex messages step by step |
| **Factory** | Payment handlers | Create correct handler for card/UPI/netbanking |
| **Observer (Events)** | Payment → Webhook/Notification | Decouple payment from notification |
| **Template Method** | Spring Batch settlement | Define skeleton, override steps |
| **Circuit Breaker** | All Feign clients | Handle downstream failures |
| **Repository** | All services | Abstract data access |
| **DTO** | All controllers | Separate API model from DB model |

---

## 12. Interview Questions This Document Answers

1. **"Why microservices instead of monolith?"** → Independent scaling, isolation, team autonomy
2. **"How do services communicate?"** → Sync (Feign/REST) for critical path, async (SQS) for rest
3. **"How do you handle distributed transactions?"** → Saga pattern with SQS, eventual consistency
4. **"What happens if a service goes down?"** → Circuit breaker, graceful degradation
5. **"How do you handle configuration?"** → Centralized config server, services fetch on startup
6. **"How do services find each other?"** → Eureka service registry, name-based discovery
7. **"What caching strategy do you use?"** → Multi-level: local Caffeine → Redis → PostgreSQL
8. **"How do you ensure idempotency?"** → Redis key with 24hr TTL, check before processing
9. **"What design patterns did you use?"** → State machine, Strategy, Builder, Observer, Factory
10. **"How do you secure service-to-service calls?"** → Internal JWT, private subnet

---

## Next Step

→ Continue to **`phase1-part4-aws-free-tier-plan.md`**
