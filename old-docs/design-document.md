# PayFlow — Design Document

**Document Version:** 1.0
**Project:** PayFlow Payment Gateway & Merchant Platform
**Date:** July 2026
**Author:** Tejaswi
**Status:** Approved

---

## 1. System Overview

PayFlow is a microservices-based payment gateway that processes card, UPI, and
net banking payments. It communicates with banks via ISO 8583 protocol, provides
real-time webhooks to merchants, runs daily batch settlements, and uses AI for
fraud detection and smart payment routing.

---

## 2. High-Level Architecture (HLD)

### 2.1 System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL ACTORS                                      │
│                                                                                   │
│  ┌──────────┐   ┌──────────────┐   ┌────────────────┐   ┌──────────────────┐   │
│  │ Customer │   │   Merchant   │   │ Merchant's     │   │  Operations      │   │
│  │ (Buyer)  │   │  (Seller)    │   │ Backend Server │   │  Team (Admin)    │   │
│  └────┬─────┘   └──────┬───────┘   └───────┬────────┘   └────────┬─────────┘   │
│       │                 │                   │                      │             │
└───────┼─────────────────┼───────────────────┼──────────────────────┼─────────────┘
        │                 │                   │                      │
        │ Pays via        │ Views dashboard   │ Receives webhooks    │ Monitors
        │ Checkout        │ Manages keys      │ Calls REST API       │ system
        │                 │                   │                      │
        ▼                 ▼                   ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                   │
│                         ╔═══════════════════════════════╗                         │
│                         ║     PAYFLOW PAYMENT GATEWAY    ║                         │
│                         ║                               ║                         │
│                         ║  11 Microservices             ║                         │
│                         ║  ISO 8583 Protocol            ║                         │
│                         ║  AI Fraud Detection           ║                         │
│                         ║  Smart Routing                ║                         │
│                         ╚═══════════════════════════════╝                         │
│                                       │                                           │
└───────────────────────────────────────┼───────────────────────────────────────────┘
                                        │
                                        │ ISO 8583 (TCP)
                                        ▼
                         ┌──────────────────────────────┐
                         │     BANKING NETWORK           │
                         │  (Visa / Mastercard / NPCI)   │
                         │  [Simulated by Bank Simulator]│
                         └──────────────────────────────┘
```


### 2.2 Component Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND LAYER                                           │
│                                                                                       │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐             │
│  │ Merchant Dashboard │  │  Hosted Checkout    │  │  Developer Portal  │             │
│  │ (React + TS)       │  │  (React + TS)      │  │  (React — API Docs)│             │
│  │ Port: 3000         │  │  Port: 3001        │  │  Port: 3002        │             │
│  │                    │  │                    │  │                    │             │
│  │ • Login/Register   │  │ • Card Form       │  │ • API Reference    │             │
│  │ • Transactions     │  │ • UPI Flow        │  │ • Code Examples    │             │
│  │ • Settlements      │  │ • Net Banking     │  │ • Webhooks Guide   │             │
│  │ • API Keys         │  │ • 3DS OTP         │  │ • Postman Links    │             │
│  │ • Analytics Charts │  │ • Success/Fail    │  │ • Authentication   │             │
│  └────────┬───────────┘  └────────┬───────────┘  └────────────────────┘             │
│           │                       │                                                   │
└───────────┼───────────────────────┼───────────────────────────────────────────────────┘
            │ HTTPS                  │ HTTPS
            ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY (Spring Cloud Gateway) — Port 8080                    │
│                                                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐       │
│  │Rate Limiting │  │ JWT / API Key│  │   Request    │  │  Swagger UI        │       │
│  │(Redis-based) │  │ Validation   │  │   Routing    │  │  Aggregation       │       │
│  └─────────────┘  └──────────────┘  └──────────────┘  └────────────────────┘       │
│                                                                                       │
└───┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
    │          │          │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼          ▼          ▼
```

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          MICROSERVICES LAYER                                          │
│                                                                                       │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │
│  │ Identity   │ │ Merchant   │ │  Payment   │ │  Routing   │ │ Settlement │        │
│  │ Service    │ │ Service    │ │  Service   │ │  Service   │ │ Service    │        │
│  │ Port:8081  │ │ Port:8082  │ │ Port:8083  │ │ Port:8084  │ │ Port:8085  │        │
│  │            │ │            │ │            │ │            │ │            │        │
│  │ •Register  │ │ •Onboard   │ │ •Create    │ │ •Route     │ │ •Batch Job │        │
│  │ •Login     │ │ •API Keys  │ │ •Authorize │ │ •ISO 8583  │ │ •Fee Calc  │        │
│  │ •JWT       │ │ •Fees      │ │ •Capture   │ │ •Failover  │ │ •Payout    │        │
│  │ •Roles     │ │ •Webhook   │ │ •Refund    │ │ •AI Route  │ │ •Reports   │        │
│  │            │ │  Config    │ │ •Idempotent│ │            │ │            │        │
│  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘        │
│        │               │              │              │              │               │
│  ┌────────────┐ ┌────────────┐ ┌────────────────────────────────────────────┐       │
│  │  Webhook   │ │Notification│ │              AI / FRAUD LAYER              │       │
│  │  Service   │ │ Service    │ │                                            │       │
│  │ Port:8086  │ │ Port:8087  │ │  • Rule Engine (velocity, amount, geo)    │       │
│  │            │ │            │ │  • ML Fraud Scorer (decision tree)         │       │
│  │ •Dispatch  │ │ •Email/SMS │ │  • Anomaly Detection (Z-score)            │       │
│  │ •HMAC Sign │ │ •Templates │ │  • Smart Routing (multi-armed bandit)     │       │
│  │ •Retry     │ │ •SQS Listen│ │  • Transaction Categorization (NLP)       │       │
│  │ •DLQ       │ │            │ │                                            │       │
│  └─────┬──────┘ └─────┬──────┘ └────────────────────────────────────────────┘       │
│        │               │                                                             │
└────────┼───────────────┼─────────────────────────────────────────────────────────────┘
         │               │
         ▼               ▼
```

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           DATA & MESSAGING LAYER                                      │
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  ┌────────┐ │
│  │ PostgreSQL   │  │    Redis     │  │  DynamoDB    │  │    SQS     │  │  SNS   │ │
│  │ (AWS RDS)    │  │(ElastiCache) │  │ (Always Free)│  │(Always Free)│  │(Free)  │ │
│  │              │  │              │  │              │  │            │  │        │ │
│  │ Schemas:     │  │ • Idempotent │  │ • Webhook    │  │ • Payment  │  │ •Email │ │
│  │ • identity   │  │   keys       │  │   Events     │  │   Events Q │  │ •SMS   │ │
│  │ • merchant   │  │ • Rate limit │  │ • Routing    │  │ • Webhook  │  │        │ │
│  │ • payment    │  │ • JWT cache  │  │   Metrics    │  │   Delivery │  │        │ │
│  │ • settlement │  │ • Sessions   │  │ • Audit Trail│  │ • Notify Q │  │        │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘  └────────┘ │
│                                                                                       │
└────────────────────────────────────────────────┬──────────────────────────────────────┘
                                                 │
                                                 │ ISO 8583 (TCP Socket)
                                                 ▼
                              ┌────────────────────────────────┐
                              │       BANK SIMULATOR            │
                              │       Port: 9000               │
                              │                                │
                              │  • TCP Server (Netty)          │
                              │  • Parses ISO 8583 requests    │
                              │  • Simulates approve/decline   │
                              │  • Configurable rules          │
                              │  • Simulates latency           │
                              └────────────────────────────────┘
```

### 2.3 Infrastructure Services (Spring Cloud)

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING CLOUD INFRASTRUCTURE                    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │            SERVICE REGISTRY (Eureka) — Port 8761         │    │
│  │                                                           │    │
│  │  All services register here on startup.                   │    │
│  │  Services find each other by name, not IP.                │    │
│  │                                                           │    │
│  │  identity-service ──► registers as "IDENTITY-SERVICE"     │    │
│  │  payment-service  ──► registers as "PAYMENT-SERVICE"      │    │
│  │  routing-service  ──► registers as "ROUTING-SERVICE"      │    │
│  │  ... (all services)                                       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           CONFIG SERVER (Spring Cloud Config) — Port 8888 │    │
│  │                                                           │    │
│  │  Stores configuration for ALL services in one place.      │    │
│  │  Each service fetches its config on startup.              │    │
│  │                                                           │    │
│  │  configurations/                                          │    │
│  │  ├── identity-service.yml                                 │    │
│  │  ├── payment-service.yml                                  │    │
│  │  ├── routing-service.yml                                  │    │
│  │  └── ... (one per service)                                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```


---

## 3. Key Flow Diagrams

### 3.1 Payment Authorization Flow (End-to-End)

```
Customer          Merchant         API Gateway      Payment        Fraud         Routing        Bank
(Browser)         (Server)         (8080)           Service        Engine        Service        Simulator
   │                 │                │               │              │              │              │
   │ 1. Click Pay   │                │               │              │              │              │
   │────────────────►│               │               │              │              │              │
   │                 │               │               │              │              │              │
   │                 │ 2. POST /v1/payments          │              │              │              │
   │                 │ Headers: API-Key, Idempotency │              │              │              │
   │                 │──────────────►│               │              │              │              │
   │                 │               │               │              │              │              │
   │                 │               │ 3. Validate   │              │              │              │
   │                 │               │    API key    │              │              │              │
   │                 │               │    Rate limit │              │              │              │
   │                 │               │───────────────►              │              │              │
   │                 │               │               │              │              │              │
   │                 │               │               │ 4. Check     │              │              │
   │                 │               │               │    Idempotency (Redis)      │              │
   │                 │               │               │              │              │              │
   │                 │               │               │ 5. Fraud     │              │              │
   │                 │               │               │    Check     │              │              │
   │                 │               │               │─────────────►│              │              │
   │                 │               │               │              │              │              │
   │                 │               │               │ 6. Score: 25 │              │              │
   │                 │               │               │    APPROVE   │              │              │
   │                 │               │               │◄─────────────│              │              │
   │                 │               │               │              │              │              │
   │                 │               │               │ 7. Route     │              │              │
   │                 │               │               │    Payment   │              │              │
   │                 │               │               │─────────────────────────────►│              │
   │                 │               │               │              │              │              │
   │                 │               │               │              │              │ 8. Build ISO │
   │                 │               │               │              │              │    8583 0100 │
   │                 │               │               │              │              │──────────────►
   │                 │               │               │              │              │              │
   │                 │               │               │              │              │ 9. Response  │
   │                 │               │               │              │              │    0110      │
   │                 │               │               │              │              │    Code: 00  │
   │                 │               │               │              │              │◄──────────────
   │                 │               │               │              │              │              │
   │                 │               │               │ 10. AUTHORIZED              │              │
   │                 │               │               │◄─────────────────────────────│              │
   │                 │               │               │              │              │              │
   │                 │               │               │ 11. Save to DB              │              │
   │                 │               │               │     Publish event to SQS    │              │
   │                 │               │               │              │              │              │
   │                 │               │ 12. Response  │              │              │              │
   │                 │               │◄──────────────│              │              │              │
   │                 │               │               │              │              │              │
   │                 │ 13. {status: "authorized"}    │              │              │              │
   │                 │◄──────────────│               │              │              │              │
   │                 │               │               │              │              │              │
   │ 14. "Payment   │               │               │              │              │              │
   │    Successful"  │               │               │              │              │              │
   │◄────────────────│               │              │              │              │              │
   │                 │               │               │              │              │              │
```


### 3.2 Webhook Delivery Flow

```
Payment Service          SQS Queue           Webhook Service         Merchant Server
      │                      │                      │                      │
      │ 1. Payment captured  │                      │                      │
      │    Publish event     │                      │                      │
      │─────────────────────►│                      │                      │
      │                      │                      │                      │
      │                      │ 2. Poll message      │                      │
      │                      │◄─────────────────────│                      │
      │                      │                      │                      │
      │                      │ 3. Receive event     │                      │
      │                      │─────────────────────►│                      │
      │                      │                      │                      │
      │                      │                      │ 4. Build payload     │
      │                      │                      │    Sign with HMAC    │
      │                      │                      │                      │
      │                      │                      │ 5. POST webhook      │
      │                      │                      │─────────────────────►│
      │                      │                      │                      │
      │                      │                      │      IF SUCCESS:     │
      │                      │                      │ 6. 200 OK            │
      │                      │                      │◄─────────────────────│
      │                      │                      │                      │
      │                      │                      │ 7. Mark delivered    │
      │                      │                      │    Log to DynamoDB   │
      │                      │                      │                      │
      │                      │                      │      IF FAILURE:     │
      │                      │                      │ 6b. Timeout/5xx      │
      │                      │                      │◄─────────────────────│
      │                      │                      │                      │
      │                      │                      │ 7b. Schedule retry   │
      │                      │                      │     (5min, 30min,    │
      │                      │                      │      2hr, 24hr)      │
      │                      │                      │                      │
      │                      │                      │ After 5 failures:    │
      │                      │                      │ 8. Move to DLQ       │
      │                      │                      │                      │
```

### 3.3 Settlement Batch Flow

```
Scheduler (Midnight)     Settlement Service        PostgreSQL           Merchant Bank
       │                        │                      │                      │
       │ 1. Trigger daily       │                      │                      │
       │    settlement job      │                      │                      │
       │───────────────────────►│                      │                      │
       │                        │                      │                      │
       │                        │ 2. Fetch CAPTURED    │                      │
       │                        │    payments (T-1)    │                      │
       │                        │─────────────────────►│                      │
       │                        │                      │                      │
       │                        │ 3. Results           │                      │
       │                        │◄─────────────────────│                      │
       │                        │                      │                      │
       │                        │ 4. Group by merchant │                      │
       │                        │    Calculate:        │                      │
       │                        │    gross - refunds   │                      │
       │                        │    - MDR fee         │                      │
       │                        │    - GST on MDR      │                      │
       │                        │    = net payout      │                      │
       │                        │                      │                      │
       │                        │ 5. Create settlement │                      │
       │                        │    records           │                      │
       │                        │─────────────────────►│                      │
       │                        │                      │                      │
       │                        │ 6. Initiate payout   │                      │
       │                        │─────────────────────────────────────────────►
       │                        │                      │                      │
       │                        │ 7. Mark SETTLED      │                      │
       │                        │─────────────────────►│                      │
       │                        │                      │                      │
       │                        │ 8. Send webhook      │                      │
       │                        │    "settlement.      │                      │
       │                        │     processed"       │                      │
       │                        │    → SQS → Webhook   │                      │
       │                        │      Service         │                      │
       │                        │                      │                      │
```


### 3.4 ISO 8583 Communication Flow

```
Routing Service                  Bank Simulator (TCP Server — Port 9000)
      │                                    │
      │ 1. SELECT ROUTE                    │
      │    (Bank A, cost: cheapest)        │
      │                                    │
      │ 2. BUILD ISO 8583 MESSAGE:         │
      │    ┌─────────────────────────┐     │
      │    │ MTI: 0100               │     │
      │    │ Bitmap: 7234...         │     │
      │    │ Field 2:  PAN           │     │
      │    │ Field 3:  Processing    │     │
      │    │ Field 4:  Amount        │     │
      │    │ Field 11: STAN          │     │
      │    │ Field 41: Terminal ID   │     │
      │    │ Field 42: Merchant ID   │     │
      │    │ Field 49: Currency (356)│     │
      │    └─────────────────────────┘     │
      │                                    │
      │ 3. PACK to binary bytes            │
      │                                    │
      │ 4. SEND via TCP socket ───────────►│
      │                                    │
      │                                    │ 5. UNPACK message
      │                                    │    Validate fields
      │                                    │    Check rules:
      │                                    │    - Card valid?
      │                                    │    - Balance ok?
      │                                    │    - Not blocked?
      │                                    │
      │                                    │ 6. BUILD RESPONSE:
      │                                    │    ┌──────────────────┐
      │                                    │    │ MTI: 0110        │
      │                                    │    │ Field 38: AuthCode│
      │                                    │    │ Field 39: "00"   │
      │                                    │    │ (00 = Approved)  │
      │                                    │    └──────────────────┘
      │                                    │
      │ 7. RECEIVE response ◄──────────────│
      │                                    │
      │ 8. UNPACK binary → Java object     │
      │    Check Field 39 (Response Code)  │
      │    "00" = APPROVED                 │
      │                                    │
      │ 9. RETURN result to Payment Service│
      │                                    │
```

### 3.5 Smart Routing Decision Flow (AI)

```
Payment Request arrives at Routing Service
         │
         ▼
┌─────────────────────────────────────┐
│  EXTRACT FEATURES:                   │
│  • Card type: Visa                   │
│  • Issuing bank: SBI                 │
│  • Amount: ₹15,000                   │
│  • Time: 2:30 PM                     │
│  • Merchant category: Electronics    │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│  EVALUATE EACH ROUTE:                │
│                                      │
│  Route A (HDFC Acquirer):            │
│  • Success rate (last 1hr): 97%      │
│  • Avg latency: 180ms               │
│  • Cost: 1.8%                        │
│  • Score: 92                         │
│                                      │
│  Route B (ICICI Acquirer):           │
│  • Success rate (last 1hr): 89%      │
│  • Avg latency: 250ms               │
│  • Cost: 1.5%                        │
│  • Score: 78                         │
│                                      │
│  Route C (Axis Acquirer):            │
│  • Success rate (last 1hr): 95%      │
│  • Avg latency: 200ms               │
│  • Cost: 2.0%                        │
│  • Score: 85                         │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│  DECISION: Route A (score: 92)       │
│                                      │
│  If Route A fails → Failover to C    │
│  If Route C fails → Failover to B    │
└─────────────────────────────────────┘
```


---

## 4. Database Design (ER Diagram)

### 4.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         IDENTITY SERVICE (Schema: identity)                           │
│                                                                                       │
│  ┌──────────────────────┐         ┌──────────────────────┐                          │
│  │       users           │         │       roles           │                          │
│  ├──────────────────────┤         ├──────────────────────┤                          │
│  │ id (PK, UUID)        │         │ id (PK, SERIAL)      │                          │
│  │ email (UNIQUE)       │    ┌───►│ name (UNIQUE)        │                          │
│  │ password_hash        │    │    │ description          │                          │
│  │ full_name            │    │    └──────────────────────┘                          │
│  │ phone                │    │                                                       │
│  │ role_id (FK) ────────┼────┘    ┌──────────────────────┐                          │
│  │ email_verified       │         │   refresh_tokens      │                          │
│  │ status (ACTIVE/      │         ├──────────────────────┤                          │
│  │         SUSPENDED)   │    ┌───►│ id (PK, UUID)        │                          │
│  │ created_at           │    │    │ user_id (FK) ────────┼──── users.id             │
│  │ updated_at           │    │    │ token (UNIQUE)       │                          │
│  └──────────┬───────────┘    │    │ expires_at           │                          │
│             │                │    │ created_at           │                          │
│             └────────────────┘    └──────────────────────┘                          │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        MERCHANT SERVICE (Schema: merchant)                            │
│                                                                                       │
│  ┌──────────────────────────┐     ┌──────────────────────────┐                      │
│  │       merchants           │     │        api_keys           │                      │
│  ├──────────────────────────┤     ├──────────────────────────┤                      │
│  │ id (PK, UUID)            │     │ id (PK, UUID)            │                      │
│  │ user_id (FK → users)     │     │ merchant_id (FK) ────────┼── merchants.id       │
│  │ business_name            │     │ key_type (TEST/LIVE)     │                      │
│  │ business_type            │     │ public_key (pk_tst_xxx) │                      │
│  │ registration_number      │     │ secret_key_hash          │                      │
│  │ gst_number               │     │ status (ACTIVE/REVOKED)  │                      │
│  │ website_url              │     │ created_at               │                      │
│  │ callback_url             │     │ last_used_at             │                      │
│  │ webhook_url              │     └──────────────────────────┘                      │
│  │ webhook_secret           │                                                        │
│  │ settlement_schedule(T+1) │     ┌──────────────────────────┐                      │
│  │ mdr_percentage           │     │      fee_configs          │                      │
│  │ fixed_fee                │     ├──────────────────────────┤                      │
│  │ status (ACTIVE/PENDING/  │     │ id (PK, UUID)            │                      │
│  │         SUSPENDED)       │     │ merchant_id (FK) ────────┼── merchants.id       │
│  │ kyc_verified             │     │ payment_method (CARD/    │                      │
│  │ created_at               │     │   UPI/NETBANKING)        │                      │
│  │ updated_at               │     │ fee_type (PERCENT/FIXED) │                      │
│  └──────────────────────────┘     │ fee_value                │                      │
│                                    │ created_at               │                      │
│                                    └──────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        PAYMENT SERVICE (Schema: payment)                              │
│                                                                                       │
│  ┌──────────────────────────┐     ┌──────────────────────────┐                      │
│  │        orders             │     │       payments            │                      │
│  ├──────────────────────────┤     ├──────────────────────────┤                      │
│  │ id (PK, UUID)            │     │ id (PK, UUID)            │                      │
│  │ merchant_id (FK)         │     │ order_id (FK) ───────────┼── orders.id          │
│  │ amount                   │     │ merchant_id (FK)         │                      │
│  │ currency (INR)           │     │ amount                   │                      │
│  │ receipt (merchant ref)   │     │ currency                 │                      │
│  │ status (CREATED/PAID/    │     │ status (CREATED/         │                      │
│  │         EXPIRED)         │     │   AUTHORIZED/CAPTURED/   │                      │
│  │ notes (JSONB)            │     │   SETTLED/VOIDED/        │                      │
│  │ expires_at               │     │   REFUNDED/FAILED)       │                      │
│  │ created_at               │     │ payment_method (CARD/    │                      │
│  │ updated_at               │     │   UPI/NETBANKING/WALLET) │                      │
│  └──────────────────────────┘     │ card_last4               │                      │
│                                    │ card_network (VISA/MC)   │                      │
│                                    │ upi_vpa                  │                      │
│  ┌──────────────────────────┐     │ bank_name                │                      │
│  │       refunds             │     │ auth_code                │                      │
│  ├──────────────────────────┤     │ rrn                      │                      │
│  │ id (PK, UUID)            │     │ idempotency_key          │                      │
│  │ payment_id (FK) ─────────┼──┐  │ risk_score               │                      │
│  │ amount                   │  │  │ route_id                 │                      │
│  │ status (INITIATED/       │  │  │ captured_amount          │                      │
│  │   PROCESSED/FAILED)      │  │  │ refunded_amount          │                      │
│  │ reason                   │  │  │ authorized_at            │                      │
│  │ rrn                      │  │  │ captured_at              │                      │
│  │ created_at               │  │  │ created_at               │                      │
│  │ processed_at             │  │  │ updated_at               │                      │
│  └──────────────────────────┘  │  └──────────────────────────┘                      │
│                                 │                                                     │
│                                 └──── payments.id                                    │
│                                                                                       │
│  ┌──────────────────────────┐                                                        │
│  │  payment_state_history    │   (Audit of every state change)                       │
│  ├──────────────────────────┤                                                        │
│  │ id (PK, SERIAL)          │                                                        │
│  │ payment_id (FK)          │                                                        │
│  │ from_state               │                                                        │
│  │ to_state                 │                                                        │
│  │ event (AUTHORIZE/CAPTURE/│                                                        │
│  │        VOID/REFUND/FAIL) │                                                        │
│  │ metadata (JSONB)         │                                                        │
│  │ created_at               │                                                        │
│  └──────────────────────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      SETTLEMENT SERVICE (Schema: settlement)                          │
│                                                                                       │
│  ┌──────────────────────────┐     ┌──────────────────────────┐                      │
│  │      settlements          │     │    settlement_items       │                      │
│  ├──────────────────────────┤     ├──────────────────────────┤                      │
│  │ id (PK, UUID)            │     │ id (PK, UUID)            │                      │
│  │ merchant_id (FK)         │     │ settlement_id (FK) ──────┼── settlements.id     │
│  │ settlement_date          │     │ payment_id (FK)          │                      │
│  │ gross_amount             │     │ amount                   │                      │
│  │ total_fee                │     │ fee_deducted             │                      │
│  │ gst_on_fee               │     │ net_amount               │                      │
│  │ net_amount               │     │ type (PAYMENT/REFUND)    │                      │
│  │ total_transactions       │     │ created_at               │                      │
│  │ total_refunds            │     └──────────────────────────┘                      │
│  │ status (INITIATED/       │                                                        │
│  │   PROCESSED/FAILED)      │     ┌──────────────────────────┐                      │
│  │ payout_reference         │     │       payouts             │                      │
│  │ bank_account_id          │     ├──────────────────────────┤                      │
│  │ created_at               │     │ id (PK, UUID)            │                      │
│  │ processed_at             │     │ settlement_id (FK) ──────┼── settlements.id     │
│  └──────────────────────────┘     │ merchant_id              │                      │
│                                    │ amount                   │                      │
│                                    │ bank_account_number      │                      │
│                                    │ ifsc_code                │                      │
│                                    │ status (PENDING/DONE/    │                      │
│                                    │         FAILED)          │                      │
│                                    │ utr_number               │                      │
│                                    │ created_at               │                      │
│                                    │ completed_at             │                      │
│                                    └──────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      DYNAMODB TABLES (NoSQL)                                          │
│                                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐                   │
│  │  Table: webhook_events                                        │                   │
│  │  PK: event_id (UUID)                                          │                   │
│  │  SK: merchant_id#created_at                                   │                   │
│  │  Attributes:                                                  │                   │
│  │    event_type, payload, delivery_status, attempt_count,       │                   │
│  │    last_attempt_at, next_retry_at, merchant_webhook_url,      │                   │
│  │    response_code, response_body, signature                    │                   │
│  │  GSI: merchant_id-index (query by merchant)                   │                   │
│  │  GSI: status-index (query pending deliveries)                 │                   │
│  │  TTL: expires_at (auto-delete after 30 days)                  │                   │
│  └──────────────────────────────────────────────────────────────┘                   │
│                                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐                   │
│  │  Table: routing_metrics                                       │                   │
│  │  PK: route_id                                                 │                   │
│  │  SK: timestamp (hour-level granularity)                       │                   │
│  │  Attributes:                                                  │                   │
│  │    total_attempts, success_count, failure_count,              │                   │
│  │    avg_latency_ms, success_rate, last_failure_reason          │                   │
│  │  TTL: expires_at (auto-delete after 7 days)                   │                   │
│  └──────────────────────────────────────────────────────────────┘                   │
│                                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐                   │
│  │  Table: audit_trail                                           │                   │
│  │  PK: entity_id (payment_id / merchant_id)                    │                   │
│  │  SK: timestamp#action                                         │                   │
│  │  Attributes:                                                  │                   │
│  │    action, actor, old_value, new_value, ip_address            │                   │
│  │  TTL: expires_at (auto-delete after 90 days)                  │                   │
│  └──────────────────────────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```


### 4.2 Table Relationships Summary

```
users (1) ──────────────── (1) merchants        (user owns one merchant account)
merchants (1) ──────────── (N) api_keys         (merchant has many API keys)
merchants (1) ──────────── (N) fee_configs      (merchant has fee per method)
merchants (1) ──────────── (N) orders           (merchant creates many orders)
orders (1) ─────────────── (N) payments         (order can have multiple attempts)
payments (1) ───────────── (N) refunds          (payment can have multiple refunds)
payments (1) ───────────── (N) payment_state_history
merchants (1) ──────────── (N) settlements      (merchant gets daily settlements)
settlements (1) ────────── (N) settlement_items  (settlement has many payments)
settlements (1) ────────── (1) payouts           (one payout per settlement)
```

---

## 5. AI Features — Detailed Design

### 5.1 AI Feature #1: Smart Fraud Detection

**Purpose:** Score every transaction for fraud risk (0-100) and auto-decide approve/decline.

**Architecture:**

```
Payment Service                    Fraud Detection Engine
      │                                    │
      │  POST /internal/fraud/evaluate     │
      │  {                                 │
      │    transactionId, cardHash,        │
      │    amount, merchantId,             │
      │    deviceFingerprint, ipAddress,   │
      │    timestamp, customerHistory      │
      │  }                                 │
      │───────────────────────────────────►│
      │                                    │
      │                          ┌─────────┼─────────────┐
      │                          │  LAYER 1: RULES       │
      │                          │  Velocity check       │
      │                          │  Amount threshold     │
      │                          │  Geo anomaly          │
      │                          │  Time-of-day check    │
      │                          │  Device check         │
      │                          │  → Rule Score: 35     │
      │                          └─────────┼─────────────┘
      │                                    │
      │                          ┌─────────┼─────────────┐
      │                          │  LAYER 2: ML MODEL    │
      │                          │  Decision Tree input: │
      │                          │  [amount, hour,       │
      │                          │   velocity, device_   │
      │                          │   age, merchant_risk] │
      │                          │  → ML Score: 28       │
      │                          └─────────┼─────────────┘
      │                                    │
      │                          ┌─────────┼─────────────┐
      │                          │  FINAL SCORE:          │
      │                          │  (Rules×0.6)+(ML×0.4) │
      │                          │  = 35×0.6 + 28×0.4   │
      │                          │  = 21 + 11.2 = 32.2  │
      │                          │                       │
      │                          │  DECISION: APPROVE    │
      │                          │  (score < 40)         │
      │                          └───────────────────────┘
      │                                    │
      │  Response: {                       │
      │    riskScore: 32,                  │
      │    decision: "APPROVE",            │
      │    reasons: [],                    │
      │    processingTimeMs: 8             │
      │  }                                 │
      │◄───────────────────────────────────│
```

**Fraud Rules (Configurable):**

| # | Rule | Condition | Score Points |
|---|------|-----------|-------------|
| 1 | High velocity | >5 txns in 5 minutes from same card | +30 |
| 2 | Large amount | Amount > ₹50,000 | +20 |
| 3 | Very large amount | Amount > ₹2,00,000 | +40 |
| 4 | Night transaction | Between 1AM-5AM and > ₹10,000 | +15 |
| 5 | New device | Device never seen before for this card | +20 |
| 6 | Different city | Different from usual city | +10 |
| 7 | Different country | Different country | +35 |
| 8 | Multiple declines | >3 declines in last hour | +25 |
| 9 | First transaction | Very first txn on card > ₹20,000 | +15 |
| 10 | High-risk merchant | Merchant category in high-risk list | +10 |

**Decision Matrix:**

| Score Range | Decision | Action |
|-------------|----------|--------|
| 0-40 | APPROVE | Process payment normally |
| 41-70 | CHALLENGE | Require 3D Secure OTP verification |
| 71-90 | REVIEW | Put in manual review queue, hold payment |
| 91-100 | DECLINE | Auto-reject payment |

---

### 5.2 AI Feature #2: Smart Payment Routing

**Purpose:** Choose the optimal bank/acquirer for each payment to maximize success rate
and minimize cost.

**Algorithm: Multi-Armed Bandit (Epsilon-Greedy)**

```
CONCEPT:
├── Each bank/acquirer is an "arm" of the bandit
├── We want to find which arm gives best "reward" (success)
├── But we also need to "explore" to discover new patterns
└── Epsilon-greedy: 90% exploit (use best known), 10% explore (try others)

HOW IT WORKS:
┌────────────────────────────────────────────────────┐
│  For each transaction:                              │
│                                                     │
│  1. Generate random number [0, 1]                   │
│                                                     │
│  2. If random < 0.1 (10% of time):                  │
│     → EXPLORE: Pick random route                    │
│     → This discovers if a previously bad route      │
│       has improved                                  │
│                                                     │
│  3. If random >= 0.1 (90% of time):                 │
│     → EXPLOIT: Pick route with highest score        │
│     → Score = weighted(success_rate, cost, latency) │
│                                                     │
│  4. After transaction completes:                    │
│     → Update route's success_rate and avg_latency   │
│     → Store in DynamoDB (routing_metrics table)     │
│                                                     │
│  SCORE FORMULA:                                     │
│  route_score = (success_rate × 0.5)                │
│              + ((1 - normalized_cost) × 0.3)        │
│              + ((1 - normalized_latency) × 0.2)     │
└────────────────────────────────────────────────────┘
```

**Failover Strategy:**

```
TRY Route A (best score)
  │
  ├── SUCCESS → return result
  │
  └── FAILURE (timeout / bank error)
        │
        ├── TRY Route B (second best)
        │     │
        │     ├── SUCCESS → return result
        │     │
        │     └── FAILURE
        │           │
        │           └── TRY Route C (third best)
        │                 │
        │                 ├── SUCCESS → return result
        │                 └── FAILURE → return "DECLINED (all routes failed)"
        │
        └── Update Route A failure count
            If >50% failure in last 5min → circuit breaker OPEN (skip Route A)
```

---

### 5.3 AI Feature #3: Transaction Categorization

**Purpose:** Auto-categorize transactions for merchant analytics dashboard.

```
INPUT: Merchant name/description from transaction

APPROACH: Keyword-based classifier (simple NLP, zero cost)

CATEGORIES:
├── FOOD_AND_DINING    → keywords: restaurant, food, pizza, cafe, swiggy, zomato
├── SHOPPING           → keywords: amazon, flipkart, store, mall, fashion
├── TRAVEL             → keywords: airline, hotel, flight, uber, ola, irctc
├── BILLS_AND_UTILITIES→ keywords: electricity, phone, recharge, water, gas
├── ENTERTAINMENT      → keywords: movie, netflix, spotify, game, ticket
├── HEALTH             → keywords: hospital, pharmacy, doctor, medical
├── EDUCATION          → keywords: school, university, course, udemy
├── FUEL               → keywords: petrol, diesel, bp, iocl, hpcl
└── OTHER              → default category

OUTPUT: { category: "FOOD_AND_DINING", confidence: 0.85 }
```

**Optional AWS Enhancement:** Use AWS Comprehend for more accurate NLP classification
(costs pennies per request, covered by $200 credits).

---

### 5.4 AI Feature #4: Anomaly Detection (Spending Patterns)

**Purpose:** Detect compromised cards by identifying unusual spending behavior.

```
ALGORITHM: Z-Score Analysis

FOR EACH CARD/MERCHANT:
1. Calculate rolling average (last 30 days):
   mean_amount = average of last 30 transactions
   std_dev = standard deviation

2. For current transaction:
   z_score = (current_amount - mean_amount) / std_dev

3. Decision:
   |z_score| < 2  → NORMAL (within 2 standard deviations)
   |z_score| 2-3  → UNUSUAL (flag for monitoring)
   |z_score| > 3  → ANOMALY (alert operations team)

EXAMPLE:
├── Card usually spends: ₹500-₹2000 per transaction
├── Mean: ₹1200, Std Dev: ₹400
├── Current transaction: ₹8000
├── Z-score: (8000-1200)/400 = 17 → ANOMALY! 🚨
└── Action: Flag + trigger additional verification
```

---

### 5.5 AI Feature #5: Predictive Analytics (Dashboard)

**Purpose:** Show merchants predicted payment volumes and revenue forecasts.

```
APPROACH: Simple Moving Average + Trend Analysis

1. Calculate 7-day moving average of daily payment volume
2. Calculate trend (increasing/decreasing/stable)
3. Project next 7 days based on trend

FORMULA:
predicted_volume[tomorrow] = moving_avg_7d + (trend_slope × 1)
predicted_volume[day_after] = moving_avg_7d + (trend_slope × 2)

Shown on merchant dashboard as a forecast line on the chart.
```

---

## 6. API Documentation Strategy

### 6.1 Swagger/OpenAPI Setup (Per Service)

```
Every service has:
├── SpringDoc OpenAPI dependency
├── @OpenAPIDefinition on main class (title, version, description)
├── @Tag on controllers (group endpoints)
├── @Operation on each method (summary, description)
├── @Schema on DTOs (field descriptions, examples)
├── @ApiResponse for each response code
└── Access: http://localhost:{port}/swagger-ui.html
```

### 6.2 Aggregated Swagger at API Gateway

```
API Gateway aggregates ALL service Swagger specs into one UI:

http://localhost:8080/swagger-ui.html
├── Identity Service APIs (/identity/**)
├── Merchant Service APIs (/merchant/**)
├── Payment Service APIs (/v1/orders, /v1/payments)
├── Settlement Service APIs (/settlement/**)
├── Webhook Service APIs (/webhook/**)
└── Admin APIs (/admin/**)
```

### 6.3 Developer Portal (Like Stripe Docs)

```
A separate React application that provides:

http://localhost:3002 (or docs.payflow.com in production)
├── Getting Started
│   ├── Create Account
│   ├── Get API Keys
│   └── Make First Payment
├── API Reference
│   ├── Authentication (API keys, JWT)
│   ├── Orders API
│   ├── Payments API
│   ├── Refunds API
│   ├── Settlements API
│   └── Webhooks API
├── Guides
│   ├── Accept Card Payments
│   ├── Accept UPI Payments
│   ├── Handle Webhooks
│   ├── Test Mode vs Live Mode
│   └── Error Handling
├── Webhooks
│   ├── Event Types
│   ├── Payload Format
│   ├── Signature Verification
│   └── Retry Policy
├── Code Examples (Java, Python, Node.js, cURL)
└── Postman Collection Download
```

### 6.4 Postman Collection Structure

```
PayFlow API Collection/
├── Environment: Local (localhost URLs)
├── Environment: AWS (deployed URLs)
│
├── 📁 Authentication
│   ├── POST Register
│   ├── POST Login
│   └── POST Refresh Token
│
├── 📁 Merchant
│   ├── POST Create Merchant
│   ├── GET Get Merchant
│   ├── POST Generate API Key
│   └── PUT Update Webhook URL
│
├── 📁 Orders
│   ├── POST Create Order
│   ├── GET Get Order
│   └── GET List Orders
│
├── 📁 Payments
│   ├── POST Authorize Payment (Card)
│   ├── POST Authorize Payment (UPI)
│   ├── POST Capture Payment
│   ├── POST Void Payment
│   ├── GET Get Payment
│   └── GET List Payments
│
├── 📁 Refunds
│   ├── POST Create Refund
│   ├── GET Get Refund
│   └── GET List Refunds
│
├── 📁 Settlements
│   ├── GET List Settlements
│   ├── GET Get Settlement Detail
│   └── GET Download Report
│
├── 📁 Webhooks
│   ├── GET List Webhook Events
│   ├── GET Get Event Detail
│   └── POST Retry Webhook
│
└── 📁 Test Scenarios (Pre-configured flows)
    ├── Happy Path: Create Order → Pay → Capture → Settle
    ├── Refund Flow: Pay → Capture → Refund
    ├── Decline Flow: Pay with insufficient funds
    └── Webhook Test: Capture → Verify webhook received
```


---

## 7. Payment State Machine

### 7.1 Complete State Diagram

```
                         ┌────────────────┐
                         │                │
              ┌──────────┤    CREATED     ├──────────────────────────────┐
              │          │                │                              │
              │          └───────┬────────┘                              │
              │                  │                                        │
              │                  │ authorize()                            │ timeout (30min)
              │                  ▼                                        │
              │          ┌────────────────┐                              │
              │          │                │                              │
              │          │  PROCESSING    │                              │
              │          │                │                              │
              │          └───┬────────┬───┘                              │
              │              │        │                                   │
              │    approved  │        │ declined                         │
              │              │        │                                   │
              │              ▼        ▼                                   ▼
              │    ┌──────────────┐  ┌──────────────┐          ┌──────────────┐
              │    │              │  │              │          │              │
              │    │ AUTHORIZED   │  │   FAILED     │          │  EXPIRED     │
              │    │              │  │              │          │              │
              │    └──┬───────┬──┘  └──────────────┘          └──────────────┘
              │       │       │
              │       │       │ void()
              │       │       │
              │       │       ▼
              │       │  ┌──────────────┐
              │       │  │              │
              │       │  │   VOIDED     │ (hold released)
              │       │  │              │
              │       │  └──────────────┘
              │       │
              │       │ capture()
              │       │ (full or partial)
              │       ▼
              │  ┌──────────────┐
              │  │              │
              │  │  CAPTURED    │
              │  │              │
              │  └──┬───────┬──┘
              │     │       │
              │     │       │ refund()
              │     │       │ (full or partial)
              │     │       ▼
              │     │  ┌──────────────┐
              │     │  │              │
              │     │  │  REFUNDED    │ (money returned to customer)
              │     │  │              │
              │     │  └──────────────┘
              │     │
              │     │ settle() [batch job]
              │     ▼
              │  ┌──────────────┐
              │  │              │
              │  │  SETTLED     │ (money in merchant's bank)
              │  │              │
              │  └──────────────┘
              │
              └────────────────────────────────────────────────────────────
```

### 7.2 Valid State Transitions

| Current State | Event | Next State | Trigger |
|--------------|-------|-----------|---------|
| CREATED | authorize | PROCESSING | Customer submits payment |
| PROCESSING | bank_approved | AUTHORIZED | Bank returns code 00 |
| PROCESSING | bank_declined | FAILED | Bank returns error code |
| AUTHORIZED | capture | CAPTURED | Merchant calls /capture |
| AUTHORIZED | void | VOIDED | Merchant calls /void |
| AUTHORIZED | timeout | EXPIRED | 7-day auto-expiry |
| CAPTURED | refund (full) | REFUNDED | Merchant calls /refund |
| CAPTURED | refund (partial) | CAPTURED | Partial amount refunded |
| CAPTURED | settle | SETTLED | Daily batch job |
| CREATED | timeout | EXPIRED | 30-min order expiry |

---

## 8. Service Communication Map

### 8.1 Synchronous (REST via OpenFeign)

```
┌────────────────┐         ┌────────────────┐
│ Payment Service│────────►│ Routing Service │  "Route this payment"
└────────────────┘         └────────────────┘

┌────────────────┐         ┌────────────────┐
│ Payment Service│────────►│ Fraud Engine    │  "Score this transaction"
└────────────────┘         └────────────────┘

┌────────────────┐         ┌────────────────┐
│ API Gateway    │────────►│ Identity Service│  "Validate this JWT"
└────────────────┘         └────────────────┘

┌────────────────┐         ┌────────────────┐
│ Settlement Svc │────────►│ Payment Service │  "Get captured payments"
└────────────────┘         └────────────────┘
```

### 8.2 Asynchronous (SQS Queues)

```
┌────────────────┐   SQS    ┌────────────────┐
│ Payment Service│─────────►│ Webhook Service │  "Deliver payment.captured event"
└────────────────┘          └────────────────┘

┌────────────────┐   SQS    ┌────────────────┐
│ Payment Service│─────────►│ Notification Svc│  "Send payment receipt email"
└────────────────┘          └────────────────┘

┌────────────────┐   SQS    ┌────────────────┐
│ Settlement Svc │─────────►│ Webhook Service │  "Deliver settlement.processed"
└────────────────┘          └────────────────┘

┌────────────────┐   SQS    ┌────────────────┐
│ Webhook Service│─────────►│  DLQ (Dead      │  "Failed after 5 retries"
└────────────────┘          │  Letter Queue)  │
                            └────────────────┘
```

---

## 9. Security Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      EXTERNAL LAYER                                   │
│                                                                       │
│  Customer → HTTPS (TLS 1.3) → CloudFront/ALB → API Gateway          │
│                                                                       │
│  Authentication methods:                                             │
│  ├── Merchants: API Key (X-Api-Key header)                           │
│  ├── Dashboard users: JWT Bearer token                               │
│  └── Webhooks: HMAC-SHA256 signature (X-PayFlow-Signature)          │
│                                                                       │
│  Rate limiting: 100 requests/second per API key (Token Bucket)       │
│  Input validation: Hibernate Validator on all requests                │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      INTERNAL LAYER                                   │
│                                                                       │
│  Service-to-service: Internal JWT (short-lived, scoped)              │
│  Network: Private subnet (services not internet-accessible)          │
│  Secrets: Environment variables (AWS Parameter Store in prod)        │
│                                                                       │
│  Data protection:                                                    │
│  ├── Card numbers: NEVER stored (pass-through only)                  │
│  ├── Passwords: BCrypt (strength 12)                                 │
│  ├── API secret keys: SHA-256 hashed in DB                           │
│  ├── Webhook secrets: AES-256 encrypted                              │
│  └── JWT: RS256 (RSA) or HS256 (HMAC) signing                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 10. AWS Deployment Architecture

```
┌─────────────────────────────── AWS VPC ──────────────────────────────────┐
│                                                                            │
│  ┌─────────────── Public Subnet ──────────────────┐                       │
│  │                                                 │                       │
│  │  ┌───────────────┐    ┌───────────────┐        │                       │
│  │  │     ALB       │    │   NAT Gateway │        │                       │
│  │  │ (Load Balance)│    │  (outbound)   │        │                       │
│  │  └───────┬───────┘    └───────────────┘        │                       │
│  │          │                                      │                       │
│  └──────────┼──────────────────────────────────────┘                       │
│             │                                                              │
│  ┌──────────┼──── Private Subnet ─────────────────────────────────┐       │
│  │          ▼                                                      │       │
│  │  ┌─────────────────┐    ┌─────────────────┐                   │       │
│  │  │  EC2 Instance 1 │    │  EC2 Instance 2 │                   │       │
│  │  │  (t3.micro)     │    │  (t3.micro)     │                   │       │
│  │  │                 │    │                 │                   │       │
│  │  │  Docker:        │    │  Docker:        │                   │       │
│  │  │  • API Gateway  │    │  • Payment Svc  │                   │       │
│  │  │  • Identity Svc │    │  • Routing Svc  │                   │       │
│  │  │  • Merchant Svc │    │  • Settlement   │                   │       │
│  │  │  • Eureka       │    │  • Webhook Svc  │                   │       │
│  │  │  • Config Server│    │  • Notification │                   │       │
│  │  │                 │    │  • Bank Sim     │                   │       │
│  │  └─────────────────┘    └─────────────────┘                   │       │
│  │                                                                │       │
│  │  ┌─────────────────┐    ┌─────────────────┐                   │       │
│  │  │  RDS PostgreSQL │    │ ElastiCache Redis│                   │       │
│  │  │  (db.t3.micro)  │    │ (cache.t3.micro)│                   │       │
│  │  └─────────────────┘    └─────────────────┘                   │       │
│  │                                                                │       │
│  └────────────────────────────────────────────────────────────────┘       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘

┌─── Always Free Services (Outside VPC) ───┐
│                                           │
│  DynamoDB (webhook events, routing data)  │
│  SQS (payment events, notification queue) │
│  SNS (email/SMS notifications)            │
│  S3 + CloudFront (React frontends)        │
│  CloudWatch (monitoring, logs, alarms)    │
│                                           │
└───────────────────────────────────────────┘
```

---

## 11. How This Project Works (Complete Flow)

### Step-by-Step: A Customer Pays ₹5000 on a Merchant's Website

```
1. MERCHANT SETUP (done once):
   ├── Merchant registers on PayFlow
   ├── Gets API keys (pk_pay_xxx, sk_pay_xxx)
   ├── Sets webhook URL (https://merchant.com/webhooks)
   └── Integrates PayFlow JS SDK on their checkout page

2. CUSTOMER INITIATES PAYMENT:
   ├── Customer adds items to cart on merchant website
   ├── Clicks "Checkout" → merchant's server calls PayFlow API
   │
   │   POST http://api.payflow.com/v1/orders
   │   Headers: Authorization: Bearer sk_pay_xxx
   │   Body: { "amount": 5000, "currency": "INR", "receipt": "order_123" }
   │
   └── PayFlow returns: { "order_id": "ord_abc", "status": "created" }

3. CUSTOMER ENTERS PAYMENT DETAILS:
   ├── Customer sees PayFlow hosted checkout page
   ├── Enters card: 4111 1111 1111 1111, Exp: 12/28, CVV: 123
   ├── Clicks "Pay ₹5000"
   │
   │   POST http://api.payflow.com/v1/payments
   │   Headers: Idempotency-Key: "idem_xyz_001"
   │   Body: {
   │     "order_id": "ord_abc",
   │     "method": "card",
   │     "card": { "number": "4111...", "expiry": "1228", "cvv": "123" }
   │   }
   │
   └── Internal processing begins...

4. PAYFLOW PROCESSES PAYMENT:
   ├── API Gateway validates API key, rate limit OK
   ├── Payment Service checks idempotency key (not duplicate)
   ├── Payment Service calls Fraud Engine → risk score: 25 (APPROVE)
   ├── Payment Service calls Routing Service → route to HDFC (best success rate)
   ├── Routing Service builds ISO 8583 message (0100 Authorization Request)
   ├── Routing Service sends via TCP to Bank Simulator
   ├── Bank Simulator validates, returns 0110 with response code "00" (APPROVED)
   ├── Payment saved with status: AUTHORIZED
   └── Event published to SQS: "payment.authorized"

5. WEBHOOK DELIVERED TO MERCHANT:
   ├── Webhook Service reads from SQS
   ├── Builds payload, signs with HMAC-SHA256
   ├── POST to merchant's webhook URL
   └── Merchant's server receives: { "event": "payment.authorized", "payment_id": "pay_xyz" }

6. MERCHANT CAPTURES PAYMENT:
   ├── Merchant confirms order (stock available, ready to ship)
   │
   │   POST http://api.payflow.com/v1/payments/pay_xyz/capture
   │   Body: { "amount": 5000 }
   │
   ├── Payment status → CAPTURED
   └── Webhook: "payment.captured" → merchant fulfills order

7. DAILY SETTLEMENT (Midnight):
   ├── Settlement Service runs batch job
   ├── Finds all CAPTURED payments for today
   ├── Calculates: ₹5000 - 2% fee (₹100) - GST (₹18) = ₹4882
   ├── Creates settlement record
   ├── Initiates payout to merchant's bank
   ├── Status → SETTLED
   └── Webhook: "settlement.processed" → merchant updates accounting

8. DONE! 
   └── Customer got their product, merchant got their money (minus fees)
```


---

## 12. Tech Stack Details with Justification

### 12.1 Why Each Technology Was Chosen

| Category | Choice | Why This? | Alternatives Rejected |
|----------|--------|-----------|----------------------|
| **Language** | Java 17 | Payment industry standard, strong typing for money, thread safety | Kotlin (less jobs), Go (less Spring), Node.js (no type safety) |
| **Framework** | Spring Boot 3.2 | Largest ecosystem, most interview questions, auto-config | Quarkus (newer, less support), Micronaut (smaller community) |
| **API Gateway** | Spring Cloud Gateway | Same tech stack, reactive, no extra cost | Kong (separate infra), Nginx (no service discovery) |
| **Service Discovery** | Eureka | Simple with Spring Cloud, visual dashboard | Consul (overkill), K8s DNS (needs K8s) |
| **Config** | Spring Cloud Config | Git-backed, native Spring, change without redeploy | Vault (complex), Parameter Store (AWS lock-in) |
| **Inter-service** | OpenFeign | Declarative REST client, auto-retry, Eureka aware | RestTemplate (verbose), WebClient (complex) |
| **Resilience** | Resilience4j | Modern, lightweight, Java-native, many patterns | Hystrix (deprecated), Sentinel (less common) |
| **Database** | PostgreSQL 15 | ACID for money, row-level locking, JSONB, best free DB | MySQL (less features), MongoDB (no ACID) |
| **Cache** | Redis 7 | Data structures, TTL, atomic ops, pub/sub | Memcached (no persistence), Hazelcast (complex) |
| **NoSQL** | DynamoDB | Always free, auto-scale, perfect for logs | MongoDB (costs money on AWS), Cassandra (complex) |
| **Queue** | Amazon SQS | Always free, managed, DLQ built-in, simple | Kafka (overkill, costs $), RabbitMQ (needs server) |
| **Notifications** | Amazon SNS | Always free, email + SMS, native AWS | SendGrid (costs), Twilio (costs) |
| **ISO 8583** | Custom parser | Learning, interview depth, lightweight | jPOS (library, less understanding) |
| **TCP** | Netty | Async, high-performance, bank-grade TCP | Plain sockets (blocking, less reliable) |
| **API Docs** | SpringDoc OpenAPI 3 | Auto-generated from code, Swagger UI built-in | Springfox (deprecated), manual Swagger |
| **Batch** | Spring Batch | Chunked processing, retry, restart, skip | Custom cron (no chunking, no restart) |
| **Migration** | Flyway | SQL-based, version control, simple | Liquibase (XML, complex) |
| **Mapping** | MapStruct | Compile-time (fast), type-safe | ModelMapper (slow, reflection), manual (tedious) |
| **Frontend** | React + TypeScript | Largest market, components, type safety | Angular (heavy), Vue (smaller market) |
| **CSS** | Tailwind | Fast development, no class naming, consistent | Bootstrap (dated), Material UI (heavy) |
| **Build** | Vite | Instant dev server, fast builds | Webpack (slow), CRA (deprecated) |
| **CI/CD** | GitHub Actions | Free, native GitHub, YAML simple | Jenkins (needs server), CircleCI (limits) |
| **Container** | Docker | Standard, Docker Compose for local dev | Podman (less tooling) |
| **Cloud** | AWS | Best free tier ($200), most interview Qs | GCP (less jobs India), Azure (less free) |
| **Monitoring** | CloudWatch | Always free basic, native AWS, no extra infra | Prometheus (needs server), Datadog (expensive) |
| **Testing** | JUnit 5 + TestContainers | Modern Java test standard, real DB in tests | H2 (not real DB behavior), Spock (Groovy) |

### 12.2 API Documentation Tools

| Tool | What It Provides | How We Use It |
|------|-----------------|---------------|
| **SpringDoc OpenAPI** | Auto-generates OpenAPI 3.0 JSON from annotations | Every controller method annotated with @Operation |
| **Swagger UI** | Interactive web UI to explore and test APIs | Available at /swagger-ui.html on every service |
| **OpenAPI JSON** | Machine-readable API spec | Export for Postman, code generators, developer portal |
| **Postman** | Manual API testing with saved collections | Pre-configured requests for all endpoints |
| **Developer Portal** | Human-readable API reference (like Stripe docs) | React site with guides, code examples, try-it |

### 12.3 Swagger Configuration Per Service

```java
@OpenAPIDefinition(
    info = @Info(
        title = "PayFlow Payment Service API",
        version = "1.0",
        description = "Payment lifecycle management - orders, payments, captures, refunds",
        contact = @Contact(name = "PayFlow Team", email = "api@payflow.com")
    ),
    servers = @Server(url = "http://localhost:8083", description = "Local"),
    security = @SecurityRequirement(name = "api-key")
)
@SecurityScheme(
    name = "api-key",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-Api-Key"
)
public class PaymentServiceApplication { }
```

### 12.4 Postman Testing Setup

```
Environments:
├── Local
│   ├── base_url: http://localhost:8080
│   ├── api_key: pk_tst_local_xxx
│   └── webhook_secret: whsec_test_xxx
│
└── AWS
    ├── base_url: https://api.payflow.yourdomain.com
    ├── api_key: pk_pay_xxx
    └── webhook_secret: whsec_live_xxx

Pre-request Scripts:
├── Auto-generate Idempotency-Key for payment requests
├── Auto-add Authorization header from environment variable
└── Auto-timestamp requests

Test Scripts:
├── Verify status codes (200, 201, 400, 401, 404)
├── Verify response schema matches OpenAPI spec
├── Extract payment_id/order_id for next request
└── Assert payment state transitions are correct
```

---

## 13. Non-Functional Design Decisions

| Concern | Design Decision | Implementation |
|---------|----------------|----------------|
| **Idempotency** | Redis-based key dedup | Check before processing, store response |
| **Rate Limiting** | Token bucket per API key | Redis counter with TTL |
| **Circuit Breaker** | Fail-fast on unhealthy bank | Resilience4j, 5 failures → open 30s |
| **Retry** | Exponential backoff | 100ms, 200ms, 400ms, max 3 attempts |
| **Timeout** | Prevent hanging requests | 5s for bank calls, 30s for API requests |
| **Pagination** | Cursor-based for lists | payment_id as cursor, limit 20-100 |
| **Versioning** | URL prefix /v1/ | Backward compatible, deprecation policy |
| **Correlation ID** | Trace requests across services | UUID in header, propagated via Feign |
| **Audit** | Log every state change | DynamoDB audit_trail table |
| **Health Check** | Readiness + liveness | /actuator/health, ALB checks every 30s |

---

## 14. Document Status

| Document | Status | Notes |
|----------|--------|-------|
| README.md | ✅ Complete | Project overview, tech stack, phases |
| requirements-document.md | ✅ Complete | PRD with functional & non-functional requirements |
| design-document.md | ✅ Complete | This file — architecture, ER, flows, AI, API docs |
| phase1-project-overview.md | ✅ Complete | Introduction, microservices table, phase structure |
| phase1-part1-payment-domain-knowledge.md | ✅ Complete | Full domain knowledge |
| phase1-part2-iso8583-protocol-deep-dive.md | ⏳ Next | ISO 8583 deep dive |
| phase1-part3 through phase16-part3 | 📋 Planned | To be written phase by phase |

---

## Next Steps

Proceed to **Phase 1 Part 2** → `phase1-part2-iso8583-protocol-deep-dive.md`

Then complete remaining Phase 1 parts (setup, environment), followed by Phase 2 (detailed
database schema SQL, complete API spec with request/response examples) and begin coding
in Phase 3.
