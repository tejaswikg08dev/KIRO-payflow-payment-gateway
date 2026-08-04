# PayFlow — Microservices Overview

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

PayFlow consists of **11 microservices**. This document explains each service — what it does, its APIs, database, and how it communicates with other services.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Customer   │  │   Merchant   │  │   Merchant   │  │  Developer   │    │
│  │   Browser    │  │    Server    │  │   Browser    │  │   Browser    │    │
│  │  (Checkout)  │  │  (API Calls) │  │ (Dashboard)  │  │   (Portal)   │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────────────┘    │
│         │                  │                  │                              │
│         └──────────────────┼──────────────────┘                              │
│                            │                                                 │
│                            ▼                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    API GATEWAY (:8080)                               │   │
│  │              Routes, Rate Limits, Auth, Swagger                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                            │                                                 │
└────────────────────────────┼─────────────────────────────────────────────────┘
                             │
┌────────────────────────────┼─────────────────────────────────────────────────┐
│                            ▼                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Identity │ │ Merchant │ │ Payment  │ │ Routing  │ │Settlement│          │
│  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │ Service  │          │
│  │  :8081   │ │  :8082   │ │  :8083   │ │  :8084   │ │  :8085   │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│                                              │                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐     │ TCP                           │
│  │ Webhook  │ │ Notific. │ │   Bank   │◀────┘                               │
│  │ Service  │ │ Service  │ │Simulator │                                     │
│  │  :8086   │ │  :8087   │ │  :9000   │                                     │
│  └──────────┘ └──────────┘ └──────────┘                                     │
│                                                                              │
│                    INFRASTRUCTURE                                            │
│  ┌──────────┐ ┌──────────┐                                                  │
│  │ Service  │ │  Config  │                                                  │
│  │ Registry │ │  Server  │                                                  │
│  │  :8761   │ │  :8888   │                                                  │
│  └──────────┘ └──────────┘                                                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Service Registry (Eureka)

**Port:** 8761  
**Purpose:** Service discovery — all services register here

**How it works:**

```
1. Each service starts → registers with Eureka
   "I'm PAYMENT-SERVICE at 192.168.1.10:8083"

2. Eureka maintains registry of all services
   ┌─────────────────────────────────────────┐
   │ Service Name      │ Instances            │
   ├───────────────────┼──────────────────────┤
   │ IDENTITY-SERVICE  │ 192.168.1.5:8081    │
   │ PAYMENT-SERVICE   │ 192.168.1.10:8083   │
   │                   │ 192.168.1.11:8083   │
   │ ROUTING-SERVICE   │ 192.168.1.15:8084   │
   └─────────────────────────────────────────┘

3. Services send heartbeat every 30 seconds
   If no heartbeat → Eureka removes from registry

4. When Payment needs Routing:
   → Ask Eureka for ROUTING-SERVICE address
   → Get IP:port, make HTTP call
```

**Dashboard:** http://localhost:8761

**No Database** — stateless, in-memory registry

---

## 2. Config Server

**Port:** 8888  
**Purpose:** Centralized configuration for all services

**How it works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Config Server                                 │
│                                                                  │
│  configurations/                                                 │
│  ├── identity-service.yml                                       │
│  │   spring.datasource.url=jdbc:postgresql://...                │
│  │   jwt.secret=xxx                                              │
│  │                                                               │
│  ├── payment-service.yml                                        │
│  │   spring.datasource.url=jdbc:postgresql://...                │
│  │   redis.host=localhost                                        │
│  │                                                               │
│  └── merchant-service.yml                                       │
│      spring.datasource.url=jdbc:postgresql://...                │
│                                                                  │
│  Benefits:                                                       │
│  ✓ Change config once → all services get update                 │
│  ✓ No hardcoded values in code                                  │
│  ✓ Different configs for dev/prod (profiles)                    │
└─────────────────────────────────────────────────────────────────┘
```

**No Database** — reads from local YAML files (or Git repo)

---

## 3. API Gateway

**Port:** 8080  
**Purpose:** Single entry point for all client requests

**Responsibilities:**

| Feature | Description |
|---------|-------------|
| **Routing** | /v1/auth/* → identity-service |
| **Rate Limiting** | 100 requests/min per API key |
| **Correlation ID** | Adds unique ID to every request |
| **Swagger** | Aggregates all service APIs |

**Route Configuration:**

```yaml
routes:
  - path: /v1/auth/**     → IDENTITY-SERVICE
  - path: /v1/merchants/** → MERCHANT-SERVICE
  - path: /v1/orders/**   → PAYMENT-SERVICE
  - path: /v1/payments/** → PAYMENT-SERVICE
  - path: /v1/settlements/** → SETTLEMENT-SERVICE
  - path: /v1/webhooks/** → WEBHOOK-SERVICE
```

**Database:** Redis (for rate limiting counters)

---

## 4. Identity Service

**Port:** 8081  
**Purpose:** User authentication and authorization

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /v1/auth/register | Create new user account |
| POST | /v1/auth/login | Login, get JWT token |
| POST | /v1/auth/refresh | Refresh expired token |
| GET | /v1/auth/me | Get current user info |

**Database:** PostgreSQL (identity schema)

```sql
-- Tables
users (id, email, password_hash, full_name, phone, role, email_verified, status, created_at)

-- Roles
MERCHANT, ADMIN, CUSTOMER
```

**Security:**
- BCrypt password hashing (strength 12)
- JWT tokens (access: 15min, refresh: 7 days)

---

## 5. Merchant Service

**Port:** 8082  
**Purpose:** Merchant onboarding and management

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /v1/merchants | Register new merchant |
| GET | /v1/merchants/{id} | Get merchant details |
| POST | /v1/merchants/{id}/api-keys | Generate API keys |
| DELETE | /v1/merchants/{id}/api-keys/{keyId} | Revoke API key |
| PUT | /v1/merchants/{id}/webhook | Configure webhook URL |
| PUT | /v1/merchants/{id}/settings | Update settings |

**Database:** PostgreSQL (merchant schema)

```sql
-- Tables
merchants (id, user_id, business_name, business_type, status, kyc_verified, mdr_percentage, ...)
api_keys (id, merchant_id, secret_key_hash, public_key, key_prefix, key_type, status, created_at)
```

**API Key Types:**
- `pk_test_*` / `sk_test_*` — Test mode (simulated payments)
- `pk_live_*` / `sk_live_*` — Live mode (real payments)

---

## 6. Payment Service

**Port:** 8083  
**Purpose:** Core payment processing (orders, auth, capture, refunds)

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /v1/orders | Create payment order |
| GET | /v1/orders/{id} | Get order details |
| POST | /v1/payments | Authorize payment |
| GET | /v1/payments/{id} | Get payment details |
| POST | /v1/payments/{id}/capture | Capture authorized payment |
| POST | /v1/payments/{id}/void | Void authorization |
| POST | /v1/payments/{id}/refund | Refund captured payment |

**Database:** PostgreSQL (payment schema) + Redis

```sql
-- Tables
orders (id, merchant_id, amount, currency, status, receipt, notes, expires_at, ...)
payments (id, order_id, method, status, risk_score, auth_code, idempotency_key, ...)
refunds (id, payment_id, amount, reason, status, ...)
```

**Payment State Machine:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Payment States                                │
│                                                                  │
│  ┌─────────┐                                                    │
│  │ CREATED │──────────────────────────────────────┐             │
│  └────┬────┘                                       │             │
│       │ authorize                                  │ timeout     │
│       ▼                                            ▼             │
│  ┌────────────┐                              ┌─────────┐        │
│  │AUTHORIZED  │────────────────────────────▶ │ FAILED  │        │
│  └─────┬──────┘     fraud/decline            └─────────┘        │
│        │                                                         │
│   ┌────┴────┐                                                   │
│   │         │                                                    │
│   ▼         ▼                                                    │
│ capture   void                                                   │
│   │         │                                                    │
│   ▼         ▼                                                    │
│┌────────┐ ┌────────┐                                            │
││CAPTURED│ │ VOIDED │                                            │
│└───┬────┘ └────────┘                                            │
│    │                                                             │
│    │ settle                                                      │
│    ▼                                                             │
│┌─────────┐                                                      │
││ SETTLED │◀──────────────────────────────────────────────────   │
│└───┬─────┘                                                      │
│    │ refund                                                      │
│    ▼                                                             │
│┌──────────┐                                                     │
││ REFUNDED │                                                     │
│└──────────┘                                                     │
└─────────────────────────────────────────────────────────────────┘
```

**Calls:**
- routing-service (Feign) — for bank communication
- SQS — publishes payment events

---

## 7. Routing Service

**Port:** 8084  
**Purpose:** Smart routing to banks, ISO 8583 communication

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /internal/route | Route payment to bank |
| GET | /internal/routes | Get available routes |
| GET | /internal/metrics | Get route performance |

**How Smart Routing Works:**

```
Payment Request arrives:
Card: 4111 1111 1111 1111 (Visa)
Amount: ₹5000

┌─────────────────────────────────────────────────────────────────┐
│                    Routing Decision                              │
│                                                                  │
│  Available Routes:                                               │
│  ┌──────────┬──────────────┬───────────┬────────────┐           │
│  │  Route   │ Success Rate │  Latency  │    Cost    │           │
│  ├──────────┼──────────────┼───────────┼────────────┤           │
│  │ Bank A   │    95.2%     │   120ms   │   1.8%     │           │
│  │ Bank B   │    98.1%     │   180ms   │   2.1%     │           │
│  │ Bank C   │    91.5%     │    90ms   │   1.5%     │           │
│  └──────────┴──────────────┴───────────┴────────────┘           │
│                                                                  │
│  Decision: Bank B (highest success rate for Visa)               │
│                                                                  │
│  Failover: If Bank B fails → try Bank A                         │
└─────────────────────────────────────────────────────────────────┘
```

**Database:** DynamoDB (routing metrics) + Redis (cache)

**Calls:**
- bank-simulator (TCP, ISO 8583)

---

## 8. Settlement Service

**Port:** 8085  
**Purpose:** Daily batch settlement, fee calculation

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /v1/settlements | List settlements |
| GET | /v1/settlements/{id} | Get settlement details |
| GET | /v1/settlements/{id}/report | Download report |
| POST | /internal/trigger | Manual trigger (admin) |

**How Settlement Works:**

```
Every day at midnight:
┌─────────────────────────────────────────────────────────────────┐
│                    Settlement Batch Job                          │
│                                                                  │
│  1. Find all CAPTURED payments from yesterday                   │
│                                                                  │
│  2. Group by merchant                                            │
│                                                                  │
│  3. Calculate per merchant:                                      │
│     Gross: ₹10,000 (sum of captured payments)                   │
│     Refunds: -₹500                                               │
│     MDR Fee (2%): -₹190                                          │
│     GST on Fee (18%): -₹34.20                                   │
│     ─────────────────────────                                    │
│     Net Settlement: ₹9,275.80                                    │
│                                                                  │
│  4. Create settlement record                                     │
│                                                                  │
│  5. Initiate payout (simulated)                                 │
│                                                                  │
│  6. Send webhook to merchant                                     │
└─────────────────────────────────────────────────────────────────┘
```

**Database:** PostgreSQL (settlement schema)

---

## 9. Webhook Service

**Port:** 8086  
**Purpose:** Reliable event delivery to merchants

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /v1/webhooks/events | List webhook events |
| GET | /v1/webhooks/events/{id} | Get event details |
| POST | /v1/webhooks/events/{id}/retry | Retry delivery |

**How Webhooks Work:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Webhook Flow                                  │
│                                                                  │
│  1. Payment captured in payment-service                         │
│                                                                  │
│  2. Event published to SQS: payment-events queue                │
│     {                                                            │
│       "type": "payment.captured",                               │
│       "payment_id": "pay_xxx",                                  │
│       "merchant_id": "merch_xxx"                                │
│     }                                                            │
│                                                                  │
│  3. Webhook service consumes from SQS                           │
│                                                                  │
│  4. Build webhook payload + sign with HMAC-SHA256               │
│     Signature = HMAC(payload, merchant_webhook_secret)          │
│                                                                  │
│  5. POST to merchant's webhook URL                              │
│     Headers:                                                     │
│       X-PayFlow-Signature: sha256=xxx                           │
│       X-PayFlow-Timestamp: 1234567890                           │
│                                                                  │
│  6. If 2xx response → Mark delivered                            │
│     If error → Retry with exponential backoff                   │
│       Attempt 1: immediate                                       │
│       Attempt 2: 1 minute                                        │
│       Attempt 3: 5 minutes                                       │
│       Attempt 4: 30 minutes                                      │
│       Attempt 5: 2 hours                                         │
│     After 5 failures → Move to Dead Letter Queue                │
└─────────────────────────────────────────────────────────────────┘
```

**Database:** DynamoDB (webhook_events)

---

## 10. Notification Service

**Port:** 8087  
**Purpose:** Email/SMS notifications via AWS SNS

**APIs:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /internal/notify | Send notification |

**Notification Types:**

| Type | Channel | Trigger |
|------|---------|---------|
| Payment success | Email | After capture |
| Payment failed | Email | After failure |
| Refund processed | Email | After refund |
| Settlement completed | Email | After settlement |
| Fraud alert | Email + SMS | High risk score |

**Database:** None (stateless, uses SQS + SNS)

---

## 11. Bank Simulator

**Port:** 9000 (TCP, not HTTP)  
**Purpose:** Simulates Visa/Mastercard bank responses

**How it works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Bank Simulator                                │
│                                                                  │
│  Receives: ISO 8583 authorization request (binary over TCP)    │
│                                                                  │
│  Response Rules:                                                 │
│  ┌─────────────────────────────┬────────────────────────────┐   │
│  │ Card Number                 │ Response                    │   │
│  ├─────────────────────────────┼────────────────────────────┤   │
│  │ 4111 1111 1111 1111        │ APPROVE (code 00)          │   │
│  │ 5500 0000 0000 0004        │ APPROVE (code 00)          │   │
│  │ 4000 0000 0000 0002        │ DECLINE (code 51)          │   │
│  │ 4000 0000 0000 0069        │ DECLINE (code 14)          │   │
│  │ 4000 0000 0000 0077        │ TIMEOUT (no response)      │   │
│  └─────────────────────────────┴────────────────────────────┘   │
│                                                                  │
│  Adds random latency: 100-300ms (simulates real bank)           │
└─────────────────────────────────────────────────────────────────┘
```

**Database:** None (in-memory)

---

## Service Communication Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                    Communication Patterns                        │
│                                                                  │
│  Synchronous (HTTP/Feign):                                      │
│  ─────────────────────────                                       │
│  payment-service  ──HTTP──▶  routing-service                    │
│  api-gateway      ──HTTP──▶  all services                       │
│                                                                  │
│  Synchronous (TCP):                                              │
│  ─────────────────                                               │
│  routing-service  ──TCP───▶  bank-simulator (ISO 8583)          │
│                                                                  │
│  Asynchronous (SQS):                                             │
│  ──────────────────                                              │
│  payment-service  ──SQS───▶  webhook-service                    │
│  payment-service  ──SQS───▶  notification-service               │
│  webhook-service  ──SQS───▶  webhook-service (retry)            │
│                                                                  │
│  Discovery (Eureka):                                             │
│  ──────────────────                                              │
│  All services register with service-registry                    │
│  Services lookup each other via Eureka                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Next Steps

**Continue to:** [04-payment-domain-knowledge.md](./04-payment-domain-knowledge.md)

This will explain how payments work in the real world — authorization, capture, settlement, MDR, and more.

---

**End of Microservices Overview**

*Next: [04-payment-domain-knowledge.md](./04-payment-domain-knowledge.md)*
