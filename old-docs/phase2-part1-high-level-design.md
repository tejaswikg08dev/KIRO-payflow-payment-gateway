# Phase 2 — Part 1: High-Level Design (HLD)

> This document provides the complete high-level architecture of PayFlow.
> Everything here is what you'd present in a System Design interview.
> No code yet — just diagrams, decisions, and data flows.

---

## 1. System Requirements Summary

### 1.1 Functional (What the system does)

- Accept payments via card, UPI, net banking
- Authorize payments through bank networks (ISO 8583)
- Capture, void, and refund payments
- Route payments to optimal bank (AI-powered)
- Detect fraud (AI-powered risk scoring)
- Settle money to merchants daily (batch)
- Notify merchants via webhooks (reliable delivery)
- Provide merchant dashboard and hosted checkout

### 1.2 Non-Functional (How well it does it)

| Metric | Target |
|--------|--------|
| Latency (payment auth) | <500ms p95 |
| Availability | 99.9% |
| Throughput | 1000 TPS (design target) |
| Data durability | Zero loss for payment data |
| Consistency | Strong for payments, eventual for analytics |

---

## 2. Component Interaction Diagram

```
                    ┌─────────────────────────────────────────────────┐
                    │            INTERNET / CLIENTS                     │
                    │                                                   │
                    │  Merchant Server    Customer Browser    Admin     │
                    │       │                   │               │       │
                    └───────┼───────────────────┼───────────────┼───────┘
                            │ REST API          │ HTTPS         │
                            ▼                   ▼               ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                          AWS ALB (Load Balancer)                            │
│                    SSL Termination, Health Checks                           │
└──────────────────────────────────┬────────────────────────────────────────┘
                                   │
                                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY (Port 8080)                                 │
│                                                                             │
│  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌──────────────────────────┐ │
│  │Rate Limit│  │ Auth Check│  │  Route   │  │ Swagger UI Aggregation   │ │
│  │(Redis)   │  │ (JWT/Key) │  │ Decision │  │ (All services in one UI) │ │
│  └──────────┘  └───────────┘  └──────────┘  └──────────────────────────┘ │
└────────┬───────────┬───────────┬──────────┬──────────┬──────────┬─────────┘
         │           │           │          │          │          │
         ▼           ▼           ▼          ▼          ▼          ▼
   ┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐
   │Identity  ││Merchant  ││ Payment  ││Settlement││ Webhook  ││Notific.  │
   │Service   ││Service   ││ Service  ││Service   ││ Service  ││Service   │
   │(8081)    ││(8082)    ││ (8083)   ││(8085)    ││ (8086)   ││(8087)    │
   └────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘
        │            │           │           │           │           │
        │            │           │           │           │           │
        │            │      ┌────▼─────┐    │           │           │
        │            │      │ Routing  │    │           │           │
        │            │      │ Service  │    │           │           │
        │            │      │ (8084)   │    │           │           │
        │            │      └────┬─────┘    │           │           │
        │            │           │           │           │           │
        │            │           │ TCP/ISO8583│          │           │
        │            │           ▼           │           │           │
        │            │      ┌──────────┐    │           │           │
        │            │      │  Bank    │    │           │           │
        │            │      │Simulator │    │           │           │
        │            │      │ (9000)   │    │           │           │
        │            │      └──────────┘    │           │           │
        ▼            ▼           ▼           ▼           ▼           ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                          │
│                                                                             │
│   PostgreSQL         Redis           DynamoDB         SQS         SNS      │
│   (RDS)             (ElastiCache)    (Always Free)   (Free)      (Free)    │
│                                                                             │
│   • identity schema  • Idempotency   • webhook_events • payment_q • Email  │
│   • merchant schema  • Rate limits   • routing_metrics• webhook_q • SMS    │
│   • payment schema   • JWT cache     • audit_trail    • notify_q           │
│   • settlement schema• Routing cache                  • settle_q           │
└───────────────────────────────────────────────────────────────────────────┘
```


---

## 3. Data Flow Diagrams

### 3.1 Payment Creation & Authorization Flow

```
┌─────────┐       ┌─────────┐       ┌─────────┐       ┌─────────┐       ┌─────────┐
│Customer │       │ Gateway │       │ Payment │       │ Routing │       │  Bank   │
│Browser  │       │  8080   │       │  8083   │       │  8084   │       │  9000   │
└────┬────┘       └────┬────┘       └────┬────┘       └────┬────┘       └────┬────┘
     │                  │                 │                  │                 │
     │ POST /v1/payments│                 │                  │                 │
     │─────────────────►│                 │                  │                 │
     │                  │                 │                  │                 │
     │                  │ Validate key    │                  │                 │
     │                  │ Rate limit OK   │                  │                 │
     │                  │ Route to payment│                  │                 │
     │                  │────────────────►│                  │                 │
     │                  │                 │                  │                 │
     │                  │                 │ Check idempotency│                 │
     │                  │                 │ (Redis lookup)   │                 │
     │                  │                 │                  │                 │
     │                  │                 │ Call fraud engine │                 │
     │                  │                 │ Score: 25 (LOW)  │                 │
     │                  │                 │                  │                 │
     │                  │                 │ POST /route      │                 │
     │                  │                 │─────────────────►│                 │
     │                  │                 │                  │                 │
     │                  │                 │                  │ Select route    │
     │                  │                 │                  │ Build ISO 8583  │
     │                  │                 │                  │ 0100 Auth Req   │
     │                  │                 │                  │────────────────►│
     │                  │                 │                  │                 │
     │                  │                 │                  │ 0110 Response   │
     │                  │                 │                  │ Code: 00        │
     │                  │                 │                  │◄────────────────│
     │                  │                 │                  │                 │
     │                  │                 │ {AUTHORIZED,      │                 │
     │                  │                 │  authCode:"A1B2"} │                 │
     │                  │                 │◄─────────────────│                 │
     │                  │                 │                  │                 │
     │                  │                 │ Save to DB       │                 │
     │                  │                 │ Publish SQS event│                 │
     │                  │                 │ Store idempotency│                 │
     │                  │                 │                  │                 │
     │                  │ {status:        │                  │                 │
     │                  │  "authorized"}  │                  │                 │
     │                  │◄────────────────│                  │                 │
     │                  │                 │                  │                 │
     │ 200 {payment_id, │                 │                  │                 │
     │  status}         │                 │                  │                 │
     │◄─────────────────│                 │                  │                 │
     │                  │                 │                  │                 │
```

### 3.2 Capture & Settlement Flow

```
Merchant         Payment Service       SQS              Settlement Svc      Bank
   │                   │                │                     │                │
   │ POST /capture     │                │                     │                │
   │──────────────────►│                │                     │                │
   │                   │                │                     │                │
   │                   │ Validate state │                     │                │
   │                   │ (must be AUTHORIZED)                 │                │
   │                   │                │                     │                │
   │                   │ Update: CAPTURED│                     │                │
   │                   │                │                     │                │
   │                   │ Publish event  │                     │                │
   │                   │───────────────►│                     │                │
   │                   │                │                     │                │
   │ {status:captured} │                │                     │                │
   │◄──────────────────│                │                     │                │
   │                   │                │                     │                │
   │                   │              (MIDNIGHT — CRON TRIGGERS)               │
   │                   │                │                     │                │
   │                   │                │    Poll captured    │                │
   │                   │                │    payments (T-1)   │                │
   │                   │ GET /payments? │◄────────────────────│                │
   │                   │ status=CAPTURED│                     │                │
   │                   │◄───────────────│                     │                │
   │                   │               │                     │                │
   │                   │ Return list   │                     │                │
   │                   │──────────────►│                     │                │
   │                   │               │                     │                │
   │                   │                │    Group by merchant│                │
   │                   │                │    Calculate fees   │                │
   │                   │                │    Create settlement│                │
   │                   │                │    Initiate payout  │                │
   │                   │                │                     │───────────────►│
   │                   │                │                     │                │
   │                   │                │    Mark SETTLED     │ Payout done    │
   │                   │                │                     │◄───────────────│
   │                   │                │                     │                │
   │                   │                │    Publish event:   │                │
   │                   │                │    settlement.done  │                │
   │                   │                │◄────────────────────│                │
   │                   │                │                     │                │
```

---

## 4. Service Responsibility Matrix

| Service | Creates | Reads | Updates | Deletes |
|---------|---------|-------|---------|---------|
| **Identity** | Users, tokens | Users by email/id | Password, status | Expired tokens |
| **Merchant** | Merchants, API keys, fee configs | Merchant by key/id | Settings, webhook URL | Revoked keys |
| **Payment** | Orders, payments, refunds | Payments by merchant/id | Payment status | Expired orders |
| **Routing** | Route metrics | Metrics for scoring | Success/fail counts | Old metrics (TTL) |
| **Settlement** | Settlements, payouts | Captured payments | Settlement status | None |
| **Webhook** | Webhook events | Pending events | Delivery status | Old events (TTL) |
| **Notification** | None (stateless) | SQS messages | None | None |

---

## 5. Scalability Design

### 5.1 Horizontal Scaling Points

```
CURRENT (Learning project):
├── 2 EC2 instances → run all services in Docker
├── 1 RDS instance → all schemas
├── 1 Redis instance → all caching
└── Handles: ~100 TPS

PRODUCTION SCALING (interview answer):
├── API Gateway: 3+ instances behind ALB (auto-scaling group)
├── Payment Service: 5+ instances (highest traffic)
├── Routing Service: 3+ instances
├── Settlement: 1 instance (batch, not user-facing)
├── RDS: Primary + 2 read replicas
├── Redis: Cluster mode (3 shards, 6 nodes)
├── SQS: Auto-scales (no config needed)
└── Handles: 10,000+ TPS
```

### 5.2 Bottleneck Analysis

| Component | Bottleneck | Solution |
|-----------|-----------|----------|
| PostgreSQL writes | Single writer | Connection pooling (HikariCP), batch inserts |
| Redis | Memory limit | Eviction policy (LRU), separate clusters per concern |
| Bank TCP connections | Limited connections | Connection pool (5-10 per bank), keep-alive |
| SQS | Message throughput | Multiple consumers, batch receive (10 messages) |
| Settlement batch | Processing time | Chunk processing (Spring Batch), parallel steps |

---

## 6. Failure Scenarios & Handling

| Scenario | Detection | Recovery |
|----------|-----------|----------|
| Bank timeout (no response in 5s) | TCP timeout | Send 0400 reversal, try next route |
| Payment service crash mid-transaction | State = PROCESSING stuck | Scheduler finds stuck payments, reverses after 10min |
| Redis unavailable | Connection exception | Fallback: skip idempotency check, log warning |
| PostgreSQL unavailable | Connection exception | Circuit breaker opens, return 503 |
| SQS unavailable | SDK exception | Retry with backoff, log locally, alert ops |
| Webhook endpoint down | HTTP 5xx or timeout | Retry 5 times over 24hrs, then DLQ |
| Duplicate payment request | Idempotency key found in Redis | Return cached response (no re-processing) |
| Eureka unavailable | Service can't register | Services use cached registry, keep running |

---

## 7. Latency Budget

Total allowed: 500ms for payment authorization

```
LATENCY BREAKDOWN:
├── API Gateway processing:      10ms
├── Payment Service logic:       20ms
├── Redis idempotency check:     2ms
├── Fraud scoring:               15ms
├── Routing decision:            5ms
├── ISO 8583 encode:             1ms
├── TCP to bank + response:      200-300ms ← MOST TIME HERE
├── ISO 8583 decode:             1ms
├── DB write:                    10ms
├── SQS publish:                 5ms
├── Response to client:          10ms
└── TOTAL:                       ~280-380ms ✅ Under 500ms
```

---

## 8. Technology Mapping

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TECHNOLOGY MAP                                     │
│                                                                       │
│  Frontend:          React 18 + TypeScript + Tailwind + Vite          │
│  API Gateway:       Spring Cloud Gateway (WebFlux/Reactive)          │
│  Services:          Spring Boot 3.2 + Spring Cloud 2023.x            │
│  Communication:     OpenFeign (sync) + SQS (async)                   │
│  Discovery:         Eureka                                            │
│  Config:            Spring Cloud Config                                │
│  Auth:              Spring Security 6 + JWT + API Keys                │
│  Database:          Spring Data JPA + Hibernate + PostgreSQL 15       │
│  Cache:             Spring Data Redis + Caffeine                      │
│  NoSQL:             AWS SDK + DynamoDB                                 │
│  Messaging:         AWS SDK + SQS + SNS                               │
│  Resilience:        Resilience4j (CB, Retry, RateLimiter)            │
│  Protocol:          Custom ISO 8583 + Netty TCP                       │
│  Batch:             Spring Batch 5                                     │
│  Migrations:        Flyway                                            │
│  Mapping:           MapStruct                                         │
│  API Docs:          SpringDoc OpenAPI 3 (Swagger UI)                 │
│  Testing:           JUnit 5 + Mockito + TestContainers               │
│  CI/CD:             GitHub Actions                                    │
│  Container:         Docker + Docker Compose                           │
│  Cloud:             AWS (EC2, RDS, ElastiCache, DynamoDB, SQS, etc) │
│  Monitoring:        CloudWatch + Spring Actuator                      │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. Interview Presentation

When asked "Design a Payment Gateway" in a system design interview, present:

1. **Requirements** (2 min) — What it does, scale expectations
2. **High-level diagram** (3 min) — Draw the component diagram above
3. **Data flow** (5 min) — Walk through payment authorization flow
4. **Database design** (5 min) — Key tables and their relationships
5. **Deep dive** (10 min) — Pick one area (idempotency, routing, settlement)
6. **Scale discussion** (5 min) — How to handle 10x traffic

---

## Next Step

→ Continue to **`phase2-part2-low-level-design.md`**
