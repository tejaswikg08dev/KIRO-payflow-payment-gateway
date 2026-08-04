# PayFlow — Complete Requirements Document

**Document Version:** 2.0  
**Last Updated:** August 2026  
**Purpose:** Master reference for all functional and non-functional requirements

---

## Document Overview

This is the **complete requirements document** for PayFlow Payment Gateway. Use this as your primary reference to understand:
- What the system does (functional requirements)
- How well it should perform (non-functional requirements)
- AI/ML feature specifications
- System constraints and assumptions

**Related Documents:**
- [HLD Complete](./hld-complete.md) — High-level architecture
- [LLD Complete](./lld-complete.md) — Low-level design details
- [Database Complete](./database-complete.md) — Full database schema
- [API Complete](./api-complete.md) — All API endpoints

---

## 1. Executive Summary

PayFlow is a **production-ready Payment Gateway** that enables merchants to accept digital payments.


```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          What is PayFlow?                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   PayFlow = Stripe/Razorpay-like Payment Gateway                            │
│                                                                              │
│   ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐       │
│   │ Customer │ ──►  │ PayFlow  │ ──►  │   Bank   │ ──►  │ Merchant │       │
│   │  (Pays)  │      │(Gateway) │      │(Authorizes)│    │  (Gets $)│       │
│   └──────────┘      └──────────┘      └──────────┘      └──────────┘       │
│                                                                              │
│   Core Capabilities:                                                         │
│   • Accept card, UPI, net banking payments                                  │
│   • Process through bank networks (ISO 8583)                                │
│   • Fraud detection with AI scoring                                         │
│   • Daily settlement to merchants                                           │
│   • Webhook notifications for events                                        │
│   • Merchant dashboard for management                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Business Objectives

| # | Objective | Success Metric | Priority |
|---|-----------|----------------|----------|
| 1 | Enable merchants to accept payments | Support card, UPI, net banking, wallet | High |
| 2 | Process payments securely | 99.9% uptime, <500ms latency | High |
| 3 | Automate settlement | Daily batch settlement (T+1, T+2) | High |
| 4 | Real-time notifications | Webhook delivery within 5 seconds | High |
| 5 | Prevent fraud | Fraud detection reduces losses by 80%+ | High |
| 6 | Merchant self-service | Dashboard for API keys, analytics, settings | Medium |
| 7 | Scale horizontally | Design for 1000+ TPS | Medium |

---

## 3. Stakeholders


```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PayFlow Stakeholders                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   MERCHANT (Business)                    CUSTOMER (Payer)                   │
│   ┌─────────────────────┐               ┌─────────────────────┐             │
│   │ • Registers business│               │ • Makes payment     │             │
│   │ • Gets API keys     │               │ • Uses checkout     │             │
│   │ • Views dashboard   │               │ • Gets confirmation │             │
│   │ • Receives payouts  │               │                     │             │
│   └─────────────────────┘               └─────────────────────┘             │
│                                                                              │
│   OPERATIONS TEAM                        FINANCE TEAM                        │
│   ┌─────────────────────┐               ┌─────────────────────┐             │
│   │ • Monitors system   │               │ • Reviews settlements│            │
│   │ • Reviews fraud     │               │ • Manages fees       │            │
│   │ • Handles support   │               │ • Reconciliation     │            │
│   └─────────────────────┘               └─────────────────────┘             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Role | Description | Primary Needs |
|------|-------------|---------------|
| **Merchant** | Business accepting payments | Easy API integration, fast settlement, clear dashboard |
| **Customer** | Person making payment | Smooth checkout, multiple payment options |
| **Operations** | Internal platform team | Admin dashboard, monitoring, alerts |
| **Finance** | Settlement & reconciliation | Accurate reports, fee calculation |

---

## 4. Functional Requirements

### 4.1 Merchant Management (FR-M)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-M01 | Merchant Registration | Merchant can register with business details and KYC documents | High | 1 |
| FR-M02 | API Key Generation | System generates API keys (test + live) for merchant | High | 2 |
| FR-M03 | Webhook Configuration | Merchant can configure webhook URL for events | High | 2 |
| FR-M04 | Payment Methods | Merchant can set accepted payment methods | Medium | 2 |
| FR-M05 | Settlement Schedule | Merchant can view settlement schedule | Medium | 8 |
| FR-M06 | Key Rotation | Merchant can regenerate/rotate API keys | Medium | 2 |
| FR-M07 | Fee Configuration | Merchant can configure fee plans (percentage + fixed) | High | 8 |
| FR-M08 | Sub-merchant | Support sub-merchant model (marketplace) | Low | Future |


### 4.2 Payment Processing (FR-P)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-P01 | Create Order | Create payment order with amount, currency, merchant reference | High | 3 |
| FR-P02 | Card Payment | Process card payments (authorize + capture flow) | High | 4 |
| FR-P03 | UPI Payment | Process UPI payments (collect request flow) | High | 6 |
| FR-P04 | Net Banking | Process net banking payments (redirect flow) | Medium | 6 |
| FR-P05 | Full Capture | Support full capture of authorized payment | High | 5 |
| FR-P06 | Partial Capture | Support partial capture | Medium | 5 |
| FR-P07 | Void | Support void (cancel authorization) | High | 5 |
| FR-P08 | Full Refund | Support full refund | High | 6 |
| FR-P09 | Partial Refund | Support partial refund | Medium | 6 |
| FR-P10 | Idempotency | Prevent duplicate charges using idempotency keys | High | 3 |
| FR-P11 | Order Expiry | Auto-expire unpaid orders after 30 minutes | Medium | 3 |
| FR-P12 | 3D Secure | Support 3D Secure (OTP) verification | Medium | 4 |
| FR-P13 | State Machine | Track payment status through state machine | High | 3 |
| FR-P14 | Error Codes | Return standardized error codes | High | 3 |

**Payment State Machine:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Payment State Transitions                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                            ┌────────────┐                                   │
│                            │  CREATED   │                                   │
│                            └─────┬──────┘                                   │
│                                  │ authorize()                              │
│                                  ▼                                          │
│                         ┌────────────────┐                                  │
│                         │  PROCESSING    │                                  │
│                         └───────┬────────┘                                  │
│                    success │        │ failure                               │
│                  ┌─────────┴────────┴──────────┐                           │
│                  ▼                              ▼                           │
│           ┌────────────┐                ┌────────────┐                     │
│           │ AUTHORIZED │                │   FAILED   │                     │
│           └──────┬─────┘                └────────────┘                     │
│      capture() │ │ void()                                                  │
│           ┌────┴─┴────┐                                                    │
│           ▼           ▼                                                    │
│    ┌────────────┐ ┌────────────┐                                          │
│    │  CAPTURED  │ │   VOIDED   │                                          │
│    └──────┬─────┘ └────────────┘                                          │
│           │ refund()                                                       │
│           ▼                                                                │
│    ┌────────────┐          ┌────────────┐                                 │
│    │  REFUNDED  │ ◄─settle─│  SETTLED   │                                 │
│    └────────────┘          └────────────┘                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 4.3 Payment Routing (FR-R)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-R01 | Optimal Routing | Route payments to optimal bank/acquirer | High | 4 |
| FR-R02 | Cost-based | Support cost-based routing (cheapest acquirer) | Medium | 9 |
| FR-R03 | Success-rate | Support success-rate-based routing | Medium | 9 |
| FR-R04 | Card-type Based | Route by card type (Visa → Bank A, MC → Bank B) | Medium | 4 |
| FR-R05 | Failover | Implement failover (if Bank A fails, try Bank B) | High | 4 |
| FR-R06 | ISO 8583 | Communicate with banks via ISO 8583 protocol (TCP) | High | 4 |
| FR-R07 | AI Routing | AI-powered dynamic routing based on historical data | Medium | 9 |
| FR-R08 | Metrics | Track success rates and latency per route | Medium | 9 |

### 4.4 Settlement (FR-S)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-S01 | Daily Batch | Run daily batch settlement (midnight) | High | 8 |
| FR-S02 | Net Settlement | Calculate net settlement (payments - refunds - fees) | High | 8 |
| FR-S03 | T+n Schedules | Support T+1, T+2, T+3 settlement schedules | High | 8 |
| FR-S04 | Reports | Generate settlement reports (PDF/CSV) | Medium | 8 |
| FR-S05 | Split Settlement | Support split settlement (marketplace model) | Low | Future |
| FR-S06 | Reconciliation | Reconciliation with bank records | Medium | 8 |
| FR-S07 | Failed Retry | Handle failed settlements with retry | Medium | 8 |

### 4.5 Webhooks (FR-W)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-W01 | Payment Events | Send webhook for payment events (authorized, captured, failed) | High | 7 |
| FR-W02 | Refund Events | Send webhook for refund events | High | 7 |
| FR-W03 | Settlement Events | Send webhook for settlement events | Medium | 8 |
| FR-W04 | HMAC Signing | Sign webhooks with HMAC-SHA256 | High | 7 |
| FR-W05 | Retry | Retry failed webhooks with exponential backoff (5 attempts) | High | 7 |
| FR-W06 | DLQ | Dead Letter Queue for permanently failed webhooks | Medium | 7 |
| FR-W07 | Delivery Logs | Webhook delivery logs accessible via API | Medium | 7 |


### 4.6 Notifications (FR-N)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-N01 | Payment Email | Send payment confirmation email to customer | Medium | 7 |
| FR-N02 | Payment SMS | Send payment confirmation SMS to customer | Low | 7 |
| FR-N03 | Settlement Email | Send settlement notification to merchant | Medium | 8 |
| FR-N04 | Fraud Alert | Send fraud alert to operations team | High | 5 |
| FR-N05 | Failed Alert | Send failed payment alert | Medium | 5 |

### 4.7 Fraud Detection (FR-F)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-F01 | Rule-based | Rule-based fraud detection (velocity, amount, geo) | High | 5 |
| FR-F02 | Risk Score | Risk scoring for every transaction (0-100) | High | 5 |
| FR-F03 | Auto-approve | Auto-approve low-risk transactions (<40) | High | 5 |
| FR-F04 | Verification | Trigger additional verification for medium-risk (40-70) | Medium | 5 |
| FR-F05 | Auto-decline | Auto-decline very high-risk transactions (>90) | High | 5 |
| FR-F06 | Manual Queue | Manual review queue for flagged transactions | Medium | 9 |
| FR-F07 | AI Anomaly | AI anomaly detection using transaction patterns | Medium | 9 |
| FR-F08 | Device Fingerprint | Device fingerprinting | Low | Future |

**Fraud Score Decision Matrix:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Fraud Score Actions                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Score Range      Action                    User Experience                │
│   ───────────────────────────────────────────────────────────────────────   │
│   0-39  (Low)      AUTO APPROVE              Instant payment                │
│   40-69 (Medium)   REQUIRE 3D SECURE         OTP verification              │
│   70-89 (High)     MANUAL REVIEW             "Under review" message        │
│   90-100 (Critical) AUTO DECLINE             Payment rejected              │
│                                                                              │
│   Factors Contributing to Score:                                            │
│   • Transaction amount vs average                                           │
│   • Time of day (unusual hours)                                             │
│   • Geographic location vs history                                          │
│   • Card BIN risk category                                                  │
│   • Velocity (transactions per minute)                                      │
│   • Device fingerprint (new/known)                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 4.8 Merchant Dashboard (FR-D)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-D01 | Login/Register | Login/Register for merchant users | High | 1 |
| FR-D02 | Overview | Overview page with transaction stats and charts | High | 1 |
| FR-D03 | Transactions List | Transactions list with search, filter, pagination | High | 3 |
| FR-D04 | Transaction Detail | Transaction detail page | Medium | 3 |
| FR-D05 | Settlements Page | Settlements page with list and reports | Medium | 8 |
| FR-D06 | API Keys | API keys management page | High | 2 |
| FR-D07 | Settings | Settings page (webhooks, payment methods) | Medium | 2 |
| FR-D08 | Analytics | Analytics charts (volume, success rate, revenue) | Medium | 9 |

### 4.9 Hosted Checkout (FR-C)

| ID | Requirement | Description | Priority | Sprint |
|----|-------------|-------------|----------|--------|
| FR-C01 | Hosted Page | Hosted payment page accessible via URL | High | 3 |
| FR-C02 | Display Info | Display payment amount and merchant name | High | 3 |
| FR-C03 | Card Form | Card payment form with validation | High | 4 |
| FR-C04 | UPI Form | UPI payment flow (enter VPA) | High | 6 |
| FR-C05 | Net Banking | Net banking flow (select bank, redirect) | Medium | 6 |
| FR-C06 | 3D Secure | 3D Secure OTP page | Medium | 4 |
| FR-C07 | Result Pages | Success/failure result pages | High | 4 |
| FR-C08 | Redirect | Redirect back to merchant after completion | High | 4 |
| FR-C09 | Mobile Responsive | Mobile responsive design | High | 4 |
| FR-C10 | Branding | Merchant branding (logo, colors) | Low | 10 |

---

## 5. Non-Functional Requirements

### 5.1 Performance (NFR-P)

| ID | Requirement | Target | Measurement |
|----|-------------|--------|-------------|
| NFR-P01 | Payment authorization response | <500ms (p95) | From API request to response |
| NFR-P02 | Non-payment API response | <200ms (p95) | List, get, search operations |
| NFR-P03 | Webhook delivery | <5 seconds | From event to delivery |
| NFR-P04 | Throughput capacity | 1000 TPS | Design capacity |
| NFR-P05 | Database query | <50ms | Average query time |


### 5.2 Availability & Reliability (NFR-A)

| ID | Requirement | Target | Implementation |
|----|-------------|--------|----------------|
| NFR-A01 | System uptime | 99.9% | Multi-AZ deployment |
| NFR-A02 | Zero data loss | Guaranteed | Database transactions, retries |
| NFR-A03 | Idempotent operations | All payment writes | Redis idempotency keys |
| NFR-A04 | Graceful degradation | If one service fails, others continue | Circuit breakers |
| NFR-A05 | Retry with circuit breaker | All inter-service calls | Resilience4j |

### 5.3 Security (NFR-S)

| ID | Requirement | Target | Implementation |
|----|-------------|--------|----------------|
| NFR-S01 | HTTPS/TLS | All external communication | Load balancer termination |
| NFR-S02 | No card storage | PCI-DSS principle | Pass-through only |
| NFR-S03 | Password hashing | BCrypt (strength 12) | Spring Security |
| NFR-S04 | JWT authentication | All APIs | Access + Refresh tokens |
| NFR-S05 | API key authentication | All payment APIs | SHA-256 hashed keys |
| NFR-S06 | Rate limiting | 100 req/sec per merchant | Redis + Gateway filter |
| NFR-S07 | Webhook signing | HMAC-SHA256 | Signature header |
| NFR-S08 | Input validation | All endpoints | Bean Validation |
| NFR-S09 | Audit trail | All operations | DynamoDB audit table |

### 5.4 Scalability (NFR-SC)

| ID | Requirement | Target | Implementation |
|----|-------------|--------|----------------|
| NFR-SC01 | Horizontal scaling | Add more instances | Stateless services |
| NFR-SC02 | Connection pooling | Efficient DB usage | HikariCP |
| NFR-SC03 | Caching | Frequent reads | Redis |
| NFR-SC04 | Async processing | Non-critical operations | SQS queues |
| NFR-SC05 | Stateless services | No server-side sessions | JWT-based auth |

### 5.5 Observability (NFR-O)

| ID | Requirement | Target | Implementation |
|----|-------------|--------|----------------|
| NFR-O01 | Structured logging | JSON format | Logback + MDC |
| NFR-O02 | Correlation ID | Every request | Gateway filter |
| NFR-O03 | Health checks | All services | Spring Actuator |
| NFR-O04 | Custom metrics | Payment count, latency, errors | CloudWatch |
| NFR-O05 | Alerting | Failures | SNS notifications |


---

## 6. AI/ML Feature Specifications

### 6.1 Smart Fraud Detection

**Purpose:** AI-powered system that analyzes every transaction in real-time and predicts fraud.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Fraud Detection Pipeline                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Transaction → Extract Features → Apply Rules → ML Model → Score → Decision│
│       │               │                │             │         │       │    │
│       ▼               ▼                ▼             ▼         ▼       ▼    │
│   ┌───────┐    ┌───────────┐    ┌───────────┐  ┌────────┐  ┌────┐ ┌──────┐ │
│   │Amount │    │• Amount   │    │Velocity   │  │Decision│  │0-100│ │Approve│ │
│   │Card   │    │• Time     │    │Limits     │  │Tree    │  │Score│ │Decline│ │
│   │Time   │    │• Location │    │Geo checks │  │Model   │  │     │ │Review │ │
│   │Device │    │• History  │    │BIN checks │  │        │  │     │ │       │ │
│   └───────┘    └───────────┘    └───────────┘  └────────┘  └────┘ └──────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Features Extracted:**
- Transaction amount
- Time of day
- Customer's historical spending pattern
- Device fingerprint (new/known)
- Geographic location vs usual location
- Velocity (transactions per minute)
- Card BIN risk category
- Merchant category code risk

**ML Model:**
- Algorithm: Decision Tree / Random Forest
- Inference time: <10ms
- Retrained: Weekly

### 6.2 Smart Payment Routing

**Purpose:** AI determines the best bank/acquirer for each payment to maximize success rate and minimize cost.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Smart Routing Algorithm                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Payment Request                                                            │
│        │                                                                     │
│        ▼                                                                     │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                    Analyze Features                              │       │
│   │  • Card type (Visa/MC/RuPay)                                    │       │
│   │  • Card issuing bank                                            │       │
│   │  • Transaction amount                                            │       │
│   │  • Time of day                                                   │       │
│   └────────────────────────────┬────────────────────────────────────┘       │
│                                │                                             │
│                                ▼                                             │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                    Score Each Route                              │       │
│   │  Route A (HDFC): Success=95%, Latency=200ms, Cost=1.5%          │       │
│   │  Route B (ICICI): Success=92%, Latency=180ms, Cost=1.8%         │       │
│   │  Route C (SBI): Success=88%, Latency=250ms, Cost=1.2%           │       │
│   └────────────────────────────┬────────────────────────────────────┘       │
│                                │                                             │
│                                ▼                                             │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                    Pick Best Route                               │       │
│   │  Algorithm: Multi-armed Bandit (explore vs exploit)              │       │
│   │  Decision: Route A (highest combined score)                      │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 6.3 AI Features Summary

| # | Feature | Purpose | Algorithm | Sprint |
|---|---------|---------|-----------|--------|
| 1 | Smart Fraud Detection | Score transactions, prevent fraud | Rule engine + Decision Tree | 5, 9 |
| 2 | Smart Payment Routing | Pick best bank for each payment | Multi-armed bandit | 9 |
| 3 | Transaction Categorization | Auto-tag for analytics | Keyword NLP | 9 |
| 4 | Anomaly Detection | Detect compromised cards | Z-score statistics | 9 |
| 5 | Predictive Analytics | Forecast volumes on dashboard | Moving average + trend | 9 |

---

## 7. System Constraints

| Constraint | Detail |
|-----------|--------|
| **Budget** | AWS Free Tier + $200 credits |
| **Timeline** | ~25 weeks of development (12 sprints) |
| **Team size** | 1 developer (learning project) |
| **Card data** | Never stored (only passed through for authorization) |
| **ISO 8583** | Simulated bank (not connected to real banking network) |
| **Payment methods** | Simulated (no real money movement) |
| **KYC** | Simplified (no real document verification) |

---

## 8. Assumptions

1. This is a simulation/demo system — no real money is processed
2. Bank responses are simulated via the Bank Simulator service
3. ISO 8583 protocol is implemented for learning — connects to our simulator, not real banks
4. Settlement is simulated — no actual bank transfers
5. Email/SMS notifications use AWS SNS (sandbox mode)
6. AI models use simulated/generated training data
7. Single-currency support initially (INR)
8. No real PCI-DSS certification (but follows design principles)

---

## 9. Out of Scope (v1)

- Real bank integrations
- Real money movement
- PCI-DSS certification
- Multi-currency / forex
- Recurring payments / subscriptions
- EMI (installment) payments
- International card processing
- Mobile SDK (iOS/Android)
- Real-time push notifications (mobile)
- Multi-language support

---

## 10. Acceptance Criteria

The project is complete when:

| # | Criteria | Sprint |
|---|----------|--------|
| 1 | All 11 microservices are running and communicating | 0-1 |
| 2 | Payment can be created, authorized, captured, and settled end-to-end | 4-8 |
| 3 | ISO 8583 messages are sent to bank simulator and responses are parsed | 4 |
| 4 | Webhooks are delivered reliably with retry | 7 |
| 5 | Settlement batch runs daily and calculates correctly | 8 |
| 6 | Fraud detection scores every transaction | 5 |
| 7 | Merchant dashboard shows transactions, analytics, and settings | 1-8 |
| 8 | Hosted checkout accepts card/UPI/net banking | 4-6 |
| 9 | All services are containerized with Docker | 0+ |
| 10 | CI/CD pipeline builds, tests, and pushes images | 1+ |
| 11 | System is deployed on AWS (EC2, RDS, etc.) | 12 |
| 12 | Monitoring dashboards and alerts are configured | 11 |
| 13 | Swagger API documentation for every service | 1+ |
| 14 | Unit and integration tests pass | All |

---

## Next Document

**Continue to:** [hld-complete.md](./hld-complete.md) — High-Level Design

---

**End of Requirements Document**
