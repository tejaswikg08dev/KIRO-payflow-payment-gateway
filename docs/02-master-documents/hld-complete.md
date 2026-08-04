# PayFlow — High-Level Design (HLD) Document

**Document Version:** 2.0  
**Last Updated:** August 2026  
**Purpose:** Master reference for system architecture and high-level design

---

## Document Overview

This document provides the **high-level architecture** of PayFlow Payment Gateway, including:
- System architecture diagrams
- Microservices overview
- Technology stack decisions
- Infrastructure design
- Security architecture
- Integration patterns

**Related Documents:**
- [Requirements Complete](./requirements-complete.md) — All requirements
- [LLD Complete](./lld-complete.md) — Low-level design details
- [Database Complete](./database-complete.md) — Full database schema
- [API Complete](./api-complete.md) — All API endpoints

---

## 1. System Architecture Overview


```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                      INTERNET                                                 │
│   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐   ┌────────────────┐  │
│   │ Customer Browser │   │ Merchant Server  │   │ Merchant Browser │   │   Developer    │  │
│   │ (Hosted Checkout)│   │ (API Integration)│   │ (Dashboard)      │   │   (Dev Portal) │  │
│   │ React App :3001  │   │ Uses API Key     │   │ React App :3000  │   │ React :3002    │  │
│   └────────┬─────────┘   └────────┬─────────┘   └────────┬─────────┘   └────────────────┘  │
│            └───────────────────────┼───────────────────────┘                                  │
│                                    ▼                                                          │
└────────────────────────────────────┼──────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼──────────────────────────────────────────────────────────┐
│                            API GATEWAY (Port 8080)                                             │
│                            Spring Cloud Gateway                                                │
│  ┌────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐                      │
│  │Rate Limiter│ │Correlation ID  │ │JWT Validation  │ │ Load Balancing │                      │
│  │(Redis)     │ │Filter          │ │                │ │ (lb://)        │                      │
│  └────────────┘ └────────────────┘ └────────────────┘ └────────────────┘                      │
│  ROUTES: /v1/auth/** → IDENTITY | /v1/merchants/** → MERCHANT | /v1/orders/** → PAYMENT      │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼──────────────────────────────────────────────────────────┐
│                                BACKEND MICROSERVICES                                           │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                   │
│  │ IDENTITY      │  │ MERCHANT      │  │ PAYMENT       │  │ ROUTING       │                   │
│  │ SERVICE :8081 │  │ SERVICE :8082 │  │ SERVICE :8083 │  │ SERVICE :8084 │                   │
│  │ Auth + JWT    │  │ API Keys      │  │ Orders/Pay    │  │ ISO 8583      │                   │
│  └───────────────┘  └───────────────┘  └───────────────┘  └───────────────┘                   │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                   │
│  │ SETTLEMENT    │  │ WEBHOOK       │  │ NOTIFICATION  │  │ BANK          │                   │
│  │ SERVICE :8085 │  │ SERVICE :8086 │  │ SERVICE :8087 │  │ SIMULATOR:9000│                   │
│  │ Batch + Fees  │  │ Events + Retry│  │ Email/SMS     │  │ TCP Server    │                   │
│  └───────────────┘  └───────────────┘  └───────────────┘  └───────────────┘                   │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼──────────────────────────────────────────────────────────┐
│                            INFRASTRUCTURE SERVICES                                             │
│  ┌────────────────────────────────────┐    ┌────────────────────────────────────┐             │
│  │     SERVICE REGISTRY (Eureka)       │    │       CONFIG SERVER                 │             │
│  │     Port: 8761                      │    │       Port: 8888                    │             │
│  │     All services register here      │    │       Centralized YAML configs     │             │
│  └────────────────────────────────────┘    └────────────────────────────────────┘             │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼──────────────────────────────────────────────────────────┐
│                              DATA & MESSAGING LAYER                                            │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌────────────┐ ┌────────────┐│
│  │   PostgreSQL     │ │      Redis       │ │    DynamoDB      │ │    SQS     │ │    SNS     ││
│  │   (Port 5432)    │ │   (Port 6379)    │ │   (Port 8000)    │ │ (Queues)   │ │ (Topics)   ││
│  │ 4 schemas        │ │ Caching + Rate   │ │ Webhook events   │ │ Events     │ │ Email/SMS  ││
│  └──────────────────┘ └──────────────────┘ └──────────────────┘ └────────────┘ └────────────┘│
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Microservices Architecture


### 2.1 Services Overview

| # | Service | Port | Purpose | Database | Key Technologies |
|---|---------|------|---------|----------|------------------|
| 1 | **service-registry** | 8761 | Service discovery (Eureka) | None | Spring Cloud Netflix |
| 2 | **config-server** | 8888 | Centralized configuration | File system | Spring Cloud Config |
| 3 | **api-gateway** | 8080 | Single entry point, routing | Redis (rate limit) | Spring Cloud Gateway |
| 4 | **identity-service** | 8081 | User authentication, JWT | PostgreSQL | Spring Security, JWT |
| 5 | **merchant-service** | 8082 | Merchant management, API keys | PostgreSQL | Spring Data JPA |
| 6 | **payment-service** | 8083 | Orders, payments, state machine | PostgreSQL + Redis | Feign, Resilience4j |
| 7 | **routing-service** | 8084 | Bank routing, ISO 8583 | Redis (metrics) | Netty TCP |
| 8 | **settlement-service** | 8085 | Daily batch settlement | PostgreSQL | Spring Batch |
| 9 | **webhook-service** | 8086 | Event delivery, retry | DynamoDB | SQS, HMAC |
| 10 | **notification-service** | 8087 | Email/SMS notifications | None | AWS SNS |
| 11 | **bank-simulator** | 9000 | Simulates bank responses | None | Plain Java TCP |

### 2.2 Service Communication Patterns

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Service Communication Patterns                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   SYNCHRONOUS (HTTP/Feign):                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ API Gateway ──HTTP──► Identity Service (JWT validation)          │       │
│   │ Payment Service ──Feign──► Routing Service (get route)          │       │
│   │ Payment Service ──Feign──► Merchant Service (verify API key)    │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   ASYNCHRONOUS (SQS Queue):                                                 │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ Payment Service ──SQS──► Webhook Service (payment events)       │       │
│   │ Payment Service ──SQS──► Notification Service (send email)      │       │
│   │ Settlement Service ──SQS──► Webhook Service (settlement events) │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   TCP (Binary Protocol):                                                     │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ Routing Service ──ISO 8583/TCP──► Bank Simulator                │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

### 3.1 Backend Technologies

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| **Language** | Java | 17 (LTS) | Primary backend language |
| **Framework** | Spring Boot | 3.x | Microservices framework |
| **Cloud** | Spring Cloud | 2023.x | Service discovery, config, gateway |
| **Security** | Spring Security | 6.x | Authentication, authorization |
| **ORM** | Spring Data JPA | 3.x | Database access |
| **Batch** | Spring Batch | 5.x | Settlement batch processing |
| **HTTP Client** | OpenFeign | Latest | Service-to-service calls |
| **Resilience** | Resilience4j | Latest | Circuit breaker, retry |
| **TCP** | Netty | 4.x | ISO 8583 communication |
| **Build** | Maven | 3.9.x | Project build tool |


### 3.2 Frontend Technologies

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| **Framework** | React | 18.x | UI framework |
| **Language** | TypeScript | 5.x | Type-safe JavaScript |
| **Build Tool** | Vite | 5.x | Fast build and dev server |
| **Styling** | Tailwind CSS | 3.x | Utility-first CSS |
| **HTTP Client** | Axios | Latest | API calls |
| **Charts** | Recharts | Latest | Dashboard visualizations |
| **Forms** | React Hook Form | Latest | Form handling |
| **Routing** | React Router | 6.x | Client-side routing |

### 3.3 Database Technologies

| Database | Purpose | Local | AWS |
|----------|---------|-------|-----|
| **PostgreSQL** | Relational data (users, merchants, orders, payments) | Docker :5432 | RDS |
| **Redis** | Caching, rate limiting, idempotency | Docker :6379 | ElastiCache |
| **DynamoDB** | Webhook events, audit trail | LocalStack :8000 | DynamoDB |

### 3.4 AWS Services

| Service | Purpose | Free Tier |
|---------|---------|-----------|
| **EC2** | Run microservices | 750 hrs/month t2.micro |
| **RDS** | PostgreSQL hosting | 750 hrs/month db.t3.micro |
| **ElastiCache** | Redis hosting | 750 hrs/month cache.t3.micro |
| **DynamoDB** | NoSQL for events | 25 GB storage |
| **SQS** | Message queues | 1M requests/month |
| **SNS** | Email/SMS notifications | Always free |
| **S3** | Static file hosting | 5 GB storage |
| **CloudFront** | CDN for frontend | 1 TB/month |
| **CloudWatch** | Monitoring, logging | 10 custom metrics |

---

## 4. Payment Flow Architecture


```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Complete Payment Flow                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. CREATE ORDER                                                            │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│  │ Merchant  │───►│ Gateway   │───►│ Payment   │───►│ PostgreSQL│          │
│  │  Server   │    │ :8080     │    │ Service   │    │ (orders)  │          │
│  └───────────┘    └───────────┘    └───────────┘    └───────────┘          │
│      POST /v1/orders                                                        │
│      Returns: order_id, checkout_url                                        │
│                                                                              │
│  2. CUSTOMER PAYMENT                                                        │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐                           │
│  │ Customer  │───►│ Checkout  │───►│ Payment   │                           │
│  │ Browser   │    │ Page:3001 │    │ Service   │                           │
│  └───────────┘    └───────────┘    └───────────┘                           │
│      Enters card details on hosted checkout                                 │
│                                                                              │
│  3. AUTHORIZE PAYMENT                                                       │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│  │ Payment   │───►│ Routing   │───►│ Bank Sim  │───►│ Response  │          │
│  │ Service   │    │ Service   │    │ :9000 TCP │    │ (Approve) │          │
│  └───────────┘    └───────────┘    └───────────┘    └───────────┘          │
│      Feign call         ISO 8583 encode/decode                              │
│                                                                              │
│  4. POST-PAYMENT EVENTS                                                     │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│  │ Payment   │───►│   SQS     │───►│ Webhook   │───►│ Merchant  │          │
│  │ Service   │    │ Queue     │    │ Service   │    │ Server    │          │
│  └───────────┘    └───────────┘    └───────────┘    └───────────┘          │
│      Publish event      Consume       POST to webhook URL                   │
│                                                                              │
│  5. SETTLEMENT (Daily)                                                      │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐                           │
│  │ Scheduler │───►│Settlement │───►│ PostgreSQL│                           │
│  │ (Cron)    │    │ Service   │    │(settlement)│                          │
│  └───────────┘    └───────────┘    └───────────┘                           │
│      Midnight cron      Batch calculate fees + payout                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Security Architecture

### 5.1 Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     JWT Authentication Flow                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   1. LOGIN                                                                  │
│   ┌───────────┐    ┌───────────┐    ┌───────────┐                          │
│   │ Dashboard │───►│ Identity  │───►│ Validate  │                          │
│   │ (React)   │    │ Service   │    │ BCrypt    │                          │
│   └───────────┘    └───────────┘    └─────┬─────┘                          │
│      POST /v1/auth/login                   │                                │
│                                            ▼                                │
│                               ┌───────────────────────┐                    │
│                               │ Generate JWT Tokens   │                    │
│                               │ • Access (15 min)     │                    │
│                               │ • Refresh (7 days)    │                    │
│                               └───────────────────────┘                    │
│                                                                              │
│   2. API CALLS                                                              │
│   ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐        │
│   │ Dashboard │───►│ Gateway   │───►│ Validate  │───►│ Backend   │        │
│   │ (React)   │    │ :8080     │    │ JWT       │    │ Service   │        │
│   └───────────┘    └───────────┘    └───────────┘    └───────────┘        │
│      Header: Authorization: Bearer <token>                                  │
│                                                                              │
│   3. REFRESH TOKEN                                                          │
│   ┌───────────┐    ┌───────────┐    ┌───────────┐                          │
│   │ Dashboard │───►│ Identity  │───►│ New Tokens│                          │
│   └───────────┘    └───────────┘    └───────────┘                          │
│      POST /v1/auth/refresh with refresh token                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 5.2 API Key Authentication

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API Key Authentication                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   API Key Format: sk_live_format                │
│   • sk_ = Secret Key prefix                                                 │
│   • live/test = Environment                                                 │
│   • xxxx = 32 character random string                                       │
│                                                                              │
│   STORAGE (Never store raw key!):                                           │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ Key shown to merchant ONCE: sk_live_abc123...                    │       │
│   │ Stored in DB: SHA-256(sk_live_abc123...) = 7f3a...               │       │
│   │ Prefix stored: sk_live_abc (for display)                         │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   VALIDATION:                                                                │
│   ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐        │
│   │ Merchant  │───►│ Gateway   │───►│ Merchant  │───►│ Payment   │        │
│   │ Server    │    │ X-Api-Key │    │ Service   │    │ Service   │        │
│   └───────────┘    └───────────┘    │ Validate  │    └───────────┘        │
│                                      │ SHA-256   │                          │
│                                      └───────────┘                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Security Layers

| Layer | Security Measure | Implementation |
|-------|------------------|----------------|
| **Network** | HTTPS/TLS | ALB termination |
| **Gateway** | Rate limiting | Redis + filter |
| **Gateway** | Correlation ID | For request tracing |
| **Authentication** | JWT tokens | Access + Refresh |
| **API Keys** | SHA-256 hashing | Never store plain |
| **Passwords** | BCrypt (12 rounds) | Spring Security |
| **Webhooks** | HMAC-SHA256 signing | Signature header |
| **Input** | Validation | Bean Validation |
| **Data** | No card storage | Pass-through only |

---

## 6. Infrastructure Architecture

### 6.1 Local Development

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Local Development Stack                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   docker-compose.infra.yml (Infrastructure Only)                            │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │  PostgreSQL :5432  │  Redis :6379  │  LocalStack :4566/:8000   │       │
│   │  (4 schemas)       │  (caching)    │  (SQS, SNS, DynamoDB)     │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   IDE (IntelliJ / VS Code)                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │  Run services individually with hot reload                       │       │
│   │  mvn spring-boot:run OR IntelliJ Run Configuration              │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 6.2 AWS Production Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AWS Production Architecture                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Route 53 (DNS)                                                            │
│       │  api.payflow.com → ALB                                              │
│       ▼                                                                      │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │               Application Load Balancer (ALB)                    │       │
│   │               HTTPS termination, health checks                   │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                              │                                               │
│   ┌──────────────────────────▼──────────────────────────────────────┐       │
│   │                        VPC                                       │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │           Public Subnets (2 AZs)                         │   │       │
│   │   │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │   │       │
│   │   │   │EC2/ECS  │  │EC2/ECS  │  │EC2/ECS  │  │EC2/ECS  │   │   │       │
│   │   │   │Gateway  │  │Identity │  │Payment  │  │...more  │   │   │       │
│   │   │   └─────────┘  └─────────┘  └─────────┘  └─────────┘   │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │           Private Subnets (2 AZs)                        │   │       │
│   │   │   ┌─────────┐  ┌─────────┐  ┌─────────┐                │   │       │
│   │   │   │   RDS   │  │ElastiCac│  │DynamoDB │                │   │       │
│   │   │   │PostgreSQL│ │  Redis  │  │ Tables  │                │   │       │
│   │   │   └─────────┘  └─────────┘  └─────────┘                │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   Other AWS Services: SQS, SNS, CloudWatch, S3, CloudFront                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. CI/CD Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CI/CD Pipeline (GitHub Actions)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   BACKEND PIPELINE (ci-backend.yml):                                        │
│   ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐        │
│   │ Code  │──│ Build │──│ Test  │──│Docker │──│ Push  │──│Security│        │
│   │ Push  │  │ Maven │  │ JUnit │  │ Build │  │ ECR   │  │ Scan  │        │
│   └───────┘  └───────┘  └───────┘  └───────┘  └───────┘  └───────┘        │
│                                                                              │
│   FRONTEND PIPELINE (ci-frontend.yml):                                      │
│   ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐        │
│   │ Code  │──│ Lint  │──│ Build │──│Deploy │──│  CDN  │──│Notify │        │
│   │ Push  │  │ ESLint│  │ Vite  │  │  S3   │──│Invalidate│       │        │
│   └───────┘  └───────┘  └───────┘  └───────┘  └───────┘  └───────┘        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Project Structure

```
payflow-payment-gateway/
├── pom.xml                      ← Parent POM (all modules)
├── docker-compose.yml           ← Full stack
├── docker-compose.infra.yml     ← Infrastructure only
├── .github/workflows/           ← CI/CD pipelines
├── docs/                        ← Documentation
│
├── common-lib/                  ← Shared Java library
├── service-registry/            ← Eureka server
├── config-server/               ← Config server
├── api-gateway/                 ← Gateway
├── identity-service/            ← Auth + JWT
├── merchant-service/            ← Merchant management
├── payment-service/             ← Core payments
├── routing-service/             ← ISO 8583 routing
├── settlement-service/          ← Batch settlement
├── webhook-service/             ← Event delivery
├── notification-service/        ← Email/SMS
├── bank-simulator/              ← Bank mock
│
├── merchant-portal/             ← React dashboard
├── hosted-checkout/             ← React checkout
└── developer-portal/            ← React dev docs
```

---

## Next Document

**Continue to:** [lld-complete.md](./lld-complete.md) — Low-Level Design

---

**End of HLD Document**
