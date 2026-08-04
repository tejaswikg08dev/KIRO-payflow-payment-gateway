# PayFlow — Tech Stack Explained

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

This document explains **every technology** used in PayFlow — what it is, why we chose it, how it works, and alternatives we considered.

---

## 1. Backend Technologies

### 1.1 Java 17 (LTS)

**What is it?**
Java is a programming language. Version 17 is a Long-Term Support (LTS) release, meaning it receives updates and security patches for years.

**Why we use it:**
- Industry standard for enterprise and payment systems
- Strong type safety prevents many bugs
- Excellent library ecosystem
- Required by Spring Boot 3

**Key Java 17 Features We Use:**

```java
// Records — immutable data classes (less boilerplate)
public record PaymentRequest(String amount, String currency) {}

// Pattern matching — cleaner conditionals
if (obj instanceof String s) {
    System.out.println(s.length());
}

// Text blocks — multi-line strings
String json = """
    {
        "amount": 100,
        "currency": "INR"
    }
    """;
```

**Alternatives Considered:**

| Language | Why Not |
|----------|---------|
| Kotlin | Less common in payment industry |
| Python | Slower performance, dynamic typing |
| Go | Smaller ecosystem for enterprise |
| Node.js | Single-threaded limitations |

---

### 1.2 Spring Boot 3.2.5

**What is it?**
Spring Boot is a framework that makes it easy to create production-ready Java applications with minimal configuration.

**Why we use it:**
- Auto-configuration reduces boilerplate
- Embedded server (no separate Tomcat needed)
- Production-ready features (health checks, metrics)
- Massive community and documentation

**How Spring Boot Works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Traditional Java App                          │
│                                                                  │
│  1. Write code                                                   │
│  2. Configure XML (hundreds of lines)                           │
│  3. Download and configure Tomcat                                │
│  4. Build WAR file                                               │
│  5. Deploy to Tomcat                                             │
│  6. Configure logging separately                                 │
│  7. Configure database connection separately                     │
│                                                                  │
│  Time: Hours of setup before writing business logic              │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot App                               │
│                                                                  │
│  1. Write code                                                   │
│  2. Add application.yml (few lines)                             │
│  3. Run: mvn spring-boot:run                                    │
│                                                                  │
│  Time: Minutes to get started                                    │
│                                                                  │
│  Spring Boot auto-configures:                                    │
│  ✓ Embedded Tomcat                                              │
│  ✓ Database connection pool                                      │
│  ✓ Logging                                                       │
│  ✓ Security defaults                                             │
│  ✓ Actuator endpoints                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

### 1.3 Spring Cloud

**What is it?**
Spring Cloud provides tools for building microservices — service discovery, configuration management, API gateway, etc.

**Components We Use:**

| Component | Purpose | Port |
|-----------|---------|------|
| Spring Cloud Gateway | API routing, rate limiting | 8080 |
| Spring Cloud Netflix Eureka | Service discovery | 8761 |
| Spring Cloud Config | Centralized configuration | 8888 |
| Spring Cloud OpenFeign | REST client for service-to-service calls | N/A |

**Why Service Discovery?**

```
WITHOUT Service Discovery:
┌─────────────────────────────────────────────────────────────────┐
│ Payment Service needs to call Routing Service                    │
│                                                                  │
│ Problem: What's the URL?                                         │
│ - localhost:8084? (only works locally)                          │
│ - routing-service.prod.aws.com:8084? (hardcoded, brittle)       │
│ - What if Routing Service moves or scales to 3 instances?       │
└─────────────────────────────────────────────────────────────────┘

WITH Service Discovery (Eureka):
┌─────────────────────────────────────────────────────────────────┐
│ 1. Routing Service starts → registers with Eureka               │
│    "I'm ROUTING-SERVICE, I'm at 192.168.1.10:8084"             │
│                                                                  │
│ 2. Payment Service needs Routing Service                         │
│    → Asks Eureka: "Where is ROUTING-SERVICE?"                   │
│    ← Eureka responds: "192.168.1.10:8084"                       │
│                                                                  │
│ 3. If Routing scales to 3 instances:                            │
│    Eureka knows all 3 IPs → load balances automatically         │
└─────────────────────────────────────────────────────────────────┘
```

---

### 1.4 Spring Security 6

**What is it?**
Framework for authentication (who are you?) and authorization (what can you do?).

**Authentication Methods in PayFlow:**

| Method | Used For | How |
|--------|----------|-----|
| JWT Tokens | User login sessions | Bearer token in header |
| API Keys | Merchant API access | X-Api-Key header |
| BCrypt | Password storage | One-way hashing |

**JWT Flow:**

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. User logs in                                                  │
│    POST /auth/login { email, password }                         │
│                                                                  │
│ 2. Server validates credentials                                  │
│    → If valid, generate JWT                                      │
│                                                                  │
│ 3. JWT Structure:                                                │
│    ┌─────────────────────────────────────────────────────────┐  │
│    │ HEADER.PAYLOAD.SIGNATURE                                 │  │
│    │                                                          │  │
│    │ Header:  {"alg":"HS256","typ":"JWT"}                    │  │
│    │ Payload: {"sub":"user@email.com","role":"MERCHANT",     │  │
│    │           "exp":1234567890}                              │  │
│    │ Signature: HMAC-SHA256(header+payload, secret)          │  │
│    └─────────────────────────────────────────────────────────┘  │
│                                                                  │
│ 4. Client stores JWT, sends with every request:                 │
│    Authorization: Bearer eyJhbGc...                              │
│                                                                  │
│ 5. Server validates JWT signature (no database lookup needed!)   │
└─────────────────────────────────────────────────────────────────┘
```

---

### 1.5 Spring Data JPA

**What is it?**
JPA (Java Persistence API) maps Java objects to database tables. Spring Data JPA adds repository pattern for easy CRUD operations.

**How JPA Works:**

```java
// You write this Java class:
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
}

// JPA automatically creates this SQL table:
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    amount DECIMAL(19,2),
    currency VARCHAR(3),
    status VARCHAR(50)
);

// You write this interface:
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByStatus(PaymentStatus status);
}

// Spring Data JPA generates implementation:
// SELECT * FROM payments WHERE status = ?
```

---

### 1.6 Spring Batch

**What is it?**
Framework for batch processing — processing large amounts of data in scheduled jobs.

**Why we use it:**
Settlement runs once per day at midnight, processing all captured payments.

**How it works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Settlement Batch Job                          │
│                                                                  │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐              │
│  │  READER  │ ───▶ │PROCESSOR │ ───▶ │  WRITER  │              │
│  │          │      │          │      │          │              │
│  │ Read 100 │      │Calculate │      │ Write    │              │
│  │ payments │      │ fees,    │      │ to       │              │
│  │ from DB  │      │ net amt  │      │ DB       │              │
│  └──────────┘      └──────────┘      └──────────┘              │
│       │                                    │                    │
│       └─────────── CHUNK (100) ────────────┘                   │
│                                                                  │
│  Benefits:                                                       │
│  • Processes in chunks (not all at once — saves memory)         │
│  • Restartable (if fails at record 5000, restart from there)   │
│  • Transaction per chunk (rollback only failed chunk)           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Database Technologies

### 2.1 PostgreSQL 15

**What is it?**
Open-source relational database with ACID compliance (Atomicity, Consistency, Isolation, Durability).

**Why we use it:**
- **ACID** — Critical for financial data (payments must never be lost)
- **JSON support** — Store semi-structured data when needed
- **Mature** — Battle-tested in production systems
- **Free** — No licensing costs

**How we organize data:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database: payflow                  │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Schema: identity                                             ││
│  │ └── users (id, email, password_hash, role)                  ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Schema: merchant                                             ││
│  │ ├── merchants (id, business_name, status)                   ││
│  │ └── api_keys (id, merchant_id, key_hash, type)              ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Schema: payment                                              ││
│  │ ├── orders (id, merchant_id, amount, status)                ││
│  │ ├── payments (id, order_id, method, status)                 ││
│  │ └── refunds (id, payment_id, amount, reason)                ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Schema: settlement                                           ││
│  │ ├── settlements (id, merchant_id, amount, date)             ││
│  │ └── settlement_items (id, settlement_id, payment_id)        ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘

WHY separate schemas?
- Each microservice owns its schema
- No cross-service table access (loose coupling)
- Easy to split into separate databases later
```

---

### 2.2 Redis 7

**What is it?**
In-memory data store — extremely fast key-value database.

**Why we use it:**

| Use Case | Why Redis? |
|----------|------------|
| **Caching** | 1ms reads vs 50ms database reads |
| **Rate Limiting** | Count requests per API key per minute |
| **Idempotency** | Prevent duplicate payment charges |
| **Session** | Store JWT blacklist for logout |

**How Rate Limiting Works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Rate Limiting with Redis                      │
│                                                                  │
│  Request comes in with API key: sk_pay_abc123                   │
│                                                                  │
│  1. Build Redis key: "rate:sk_pay_abc123:minute:202608041530"  │
│                                                                  │
│  2. INCR (increment) the key                                    │
│     Redis: "rate:sk_pay_abc123:minute:202608041530" = 45       │
│                                                                  │
│  3. Check: Is 45 > 100 (limit)?                                 │
│     No → Allow request                                           │
│     Yes → Return 429 Too Many Requests                          │
│                                                                  │
│  4. Key expires after 60 seconds (auto-cleanup)                 │
│                                                                  │
│  WHY Redis?                                                      │
│  - Atomic INCR operation (no race conditions)                   │
│  - Sub-millisecond speed                                         │
│  - Auto-expiration (TTL)                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

### 2.3 DynamoDB

**What is it?**
AWS managed NoSQL database — serverless, scales automatically.

**Why we use it:**
- **Always free tier** — 25GB storage, 25 read/write capacity units
- **No server management** — AWS handles everything
- **Fast at scale** — Consistent single-digit millisecond latency

**What we store in DynamoDB:**

| Table | Purpose | Why DynamoDB? |
|-------|---------|---------------|
| webhook_events | Delivery logs | High write volume, flexible schema |
| routing_metrics | Bank success rates | Time-series data, fast reads |
| audit_trail | All operations log | Append-only, never modified |

---

### 2.4 Amazon SQS (Simple Queue Service)

**What is it?**
Managed message queue — decouple services by sending messages asynchronously.

**Why we use it:**

```
WITHOUT Queue (Synchronous):
┌─────────────────────────────────────────────────────────────────┐
│ Payment Service                                                  │
│      │                                                           │
│      ├──▶ Update Database (10ms)                                │
│      ├──▶ Call Webhook Service (200ms) ← BLOCKS!                │
│      ├──▶ Call Notification Service (150ms) ← BLOCKS!           │
│      │                                                           │
│ Total: 360ms response time                                       │
│ Problem: If Webhook Service is slow/down, Payment fails!        │
└─────────────────────────────────────────────────────────────────┘

WITH Queue (Asynchronous):
┌─────────────────────────────────────────────────────────────────┐
│ Payment Service                                                  │
│      │                                                           │
│      ├──▶ Update Database (10ms)                                │
│      ├──▶ Send to SQS Queue (5ms) ← NON-BLOCKING               │
│      │                                                           │
│ Total: 15ms response time                                        │
│                                                                  │
│ Webhook Service picks up message from queue later               │
│ If Webhook is down → messages wait in queue, not lost!          │
└─────────────────────────────────────────────────────────────────┘
```

**Our SQS Queues:**

| Queue | Producer | Consumer |
|-------|----------|----------|
| payment-events | payment-service | webhook-service |
| webhook-delivery | webhook-service | webhook-service (retry) |
| notifications | payment-service | notification-service |
| payment-events-dlq | Failed messages | Manual review |

---

## 3. Frontend Technologies

### 3.1 React 18

**What is it?**
JavaScript library for building user interfaces with reusable components.

**Why we use it:**
- Component-based architecture
- Virtual DOM for performance
- Huge ecosystem and community
- Industry standard

**Key Concepts:**

```jsx
// Component — reusable UI piece
function PaymentCard({ amount, status }) {
  return (
    <div className="card">
      <span>₹{amount}</span>
      <span className={status}>{status}</span>
    </div>
  );
}

// State — data that changes
const [payments, setPayments] = useState([]);

// Effect — side effects (API calls)
useEffect(() => {
  fetchPayments().then(setPayments);
}, []);
```

---

### 3.2 TypeScript 5

**What is it?**
JavaScript with static types — catches errors at compile time.

**Why we use it:**

```typescript
// JavaScript — error at RUNTIME (in production!)
function processPayment(payment) {
  return payment.amount * 100;  // What if payment is null?
}

// TypeScript — error at COMPILE TIME (before deployment!)
interface Payment {
  amount: number;
  currency: string;
}

function processPayment(payment: Payment): number {
  return payment.amount * 100;  // TypeScript ensures payment has amount
}
```

---

### 3.3 Vite 5

**What is it?**
Build tool for modern web projects — much faster than webpack.

**Why we use it:**
- **Fast dev server** — Hot reload in milliseconds
- **Fast builds** — Uses ESBuild (10-100x faster than webpack)
- **Simple config** — Minimal setup needed

---

### 3.4 Tailwind CSS 3

**What is it?**
Utility-first CSS framework — style with classes directly in HTML.

**Why we use it:**

```html
<!-- Traditional CSS -->
<style>
.card {
  background-color: white;
  padding: 1rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
</style>
<div class="card">Payment</div>

<!-- Tailwind CSS -->
<div class="bg-white p-4 rounded-lg shadow-sm">Payment</div>

Benefits:
✓ No switching between HTML and CSS files
✓ Consistent design system (spacing, colors)
✓ Small production bundle (unused classes removed)
```

---

## 4. DevOps Technologies

### 4.1 Docker

**What is it?**
Containerization platform — packages application with all dependencies.

**Why we use it:**
- "Works on my machine" → "Works everywhere"
- Consistent environments (dev = prod)
- Easy to scale (run multiple containers)

**How Docker Works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Without Docker                                │
│                                                                  │
│  Developer A's Machine:        Developer B's Machine:           │
│  - Java 11                     - Java 17                        │
│  - PostgreSQL 13               - PostgreSQL 15                  │
│  - Redis 6                     - No Redis                       │
│                                                                  │
│  "Works on my machine!" vs "It's broken on mine!"               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    With Docker                                   │
│                                                                  │
│  Dockerfile defines exact environment:                          │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ FROM eclipse-temurin:17-jre-alpine                         │ │
│  │ COPY target/app.jar /app.jar                               │ │
│  │ EXPOSE 8080                                                 │ │
│  │ ENTRYPOINT ["java", "-jar", "/app.jar"]                    │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Same container runs identically on:                            │
│  ✓ Developer A's laptop                                         │
│  ✓ Developer B's laptop                                         │
│  ✓ CI/CD server                                                 │
│  ✓ AWS production                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.2 GitHub Actions

**What is it?**
CI/CD platform built into GitHub — automate build, test, deploy.

**Why we use it:**
- Free for public repos, generous free tier for private
- Integrated with GitHub (no separate service)
- YAML-based configuration (version controlled)

**Our Pipelines:**

```yaml
# On every push:
# 1. Build Java services
# 2. Run tests
# 3. Build Docker image
# 4. Push to AWS ECR
# 5. Deploy to EC2
```

---

### 4.3 AWS Free Tier

**What is it?**
Amazon Web Services offers free tier for learning and small projects.

**Services We Use:**

| Service | Free Tier | Our Use |
|---------|-----------|---------|
| EC2 | 750 hrs/month t3.micro | Run Java services |
| RDS | 750 hrs/month db.t3.micro | PostgreSQL database |
| ElastiCache | 750 hrs/month cache.t3.micro | Redis cache |
| DynamoDB | 25GB + 25 RCU/WCU | Always free! |
| SQS | 1 million requests/month | Always free! |
| SNS | 1 million publishes/month | Always free! |
| S3 | 5GB storage | Frontend hosting |
| CloudFront | 1TB transfer/month | CDN |

---

## 5. Protocol & Communication

### 5.1 ISO 8583

**What is it?**
International standard for financial transaction messages — used by Visa, Mastercard, and banks worldwide.

**Why we use it:**
- Industry standard for card payments
- Learn how real payment systems communicate
- Impressive on resume/interviews

**How ISO 8583 Works:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    ISO 8583 Message Structure                    │
│                                                                  │
│  ┌──────┬────────────────────────┬─────────────────────────────┐│
│  │ MTI  │        BITMAP          │           FIELDS            ││
│  │4 byte│       16 bytes         │       Variable length       ││
│  └──────┴────────────────────────┴─────────────────────────────┘│
│                                                                  │
│  MTI (Message Type Indicator):                                   │
│  0100 = Authorization Request                                    │
│  0110 = Authorization Response                                   │
│  0400 = Reversal Request                                         │
│                                                                  │
│  BITMAP: Indicates which fields are present                      │
│  Example: Field 2 (Card Number), Field 4 (Amount)               │
│                                                                  │
│  FIELDS: Actual data (card number, amount, etc.)                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Summary: Why This Stack?

| Requirement | Technology | Why |
|-------------|------------|-----|
| Enterprise-grade backend | Java + Spring Boot | Industry standard, mature |
| Microservices | Spring Cloud | Complete toolset |
| Relational data | PostgreSQL | ACID, free, reliable |
| Caching | Redis | Fast, versatile |
| Event logs | DynamoDB | Free tier, scalable |
| Async messaging | SQS | Decoupling, reliability |
| Notifications | SNS | Email/SMS, free tier |
| Modern UI | React + TypeScript | Type safety, components |
| Styling | Tailwind CSS | Rapid development |
| Containers | Docker | Consistency |
| CI/CD | GitHub Actions | Free, integrated |
| Cloud | AWS Free Tier | Real cloud experience |
| Bank protocol | ISO 8583 | Industry learning |

---

## 7. Next Steps

**Continue to:** [03-microservices-overview.md](./03-microservices-overview.md)

This will explain each of the 11 microservices in detail — what they do, how they interact, and their APIs.

---

**End of Tech Stack Explained**

*Next: [03-microservices-overview.md](./03-microservices-overview.md)*
