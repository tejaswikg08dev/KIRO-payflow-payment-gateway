# PayFlow — Requirements Document (PRD)

**Document Version:** 1.0
**Project Name:** PayFlow Payment Gateway
**Date:** July 2026
**Author:** Tejaswi
**Status:** Approved

---

## 1. Executive Summary

PayFlow is a production-ready Payment Gateway and Merchant Platform that enables merchants
(businesses) to accept digital payments from their customers. The platform supports multiple
payment methods (cards, UPI, net banking), processes transactions through bank networks using
ISO 8583 protocol, and handles the complete payment lifecycle from authorization to settlement.

The system includes AI-powered features for fraud detection, smart payment routing, and
transaction analytics.

---

## 2. Business Objectives

| # | Objective | Success Metric |
|---|-----------|---------------|
| 1 | Enable merchants to accept payments via multiple methods | Support card, UPI, net banking, wallet |
| 2 | Process payments securely and reliably | 99.9% uptime, <500ms latency |
| 3 | Automate settlement to merchants | Daily batch settlement (T+1, T+2) |
| 4 | Notify merchants in real-time | Webhook delivery within 5 seconds |
| 5 | Prevent fraudulent transactions | Fraud detection reduces losses by 80%+ |
| 6 | Provide merchant self-service | Dashboard for API keys, analytics, settings |
| 7 | Scale horizontally | Handle 1000+ transactions/second (design goal) |

---

## 3. Stakeholders

| Role | Description | Needs |
|------|-------------|-------|
| **Merchant** | Business accepting payments | Easy integration, fast settlement, dashboard |
| **Customer** | Person making payment | Smooth checkout, multiple payment options |
| **Operations Team** | Internal team managing platform | Admin dashboard, monitoring, alerts |
| **Finance Team** | Handles settlements and reconciliation | Accurate reports, fee calculation |

---

## 4. Functional Requirements

### 4.1 Merchant Management

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-M01 | Merchant can register with business details and KYC documents | High |
| FR-M02 | System generates API keys (test + live) for merchant | High |
| FR-M03 | Merchant can configure webhook URL | High |
| FR-M04 | Merchant can set accepted payment methods | Medium |
| FR-M05 | Merchant can view settlement schedule | Medium |
| FR-M06 | Merchant can regenerate/rotate API keys | Medium |
| FR-M07 | Merchant can configure fee plans (percentage + fixed) | High |
| FR-M08 | Support sub-merchant model (marketplace) | Low |

### 4.2 Payment Processing

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-P01 | Create payment order with amount, currency, merchant reference | High |
| FR-P02 | Process card payments (authorize + capture flow) | High |
| FR-P03 | Process UPI payments (collect request flow) | High |
| FR-P04 | Process net banking payments (redirect flow) | Medium |
| FR-P05 | Support full capture of authorized payment | High |
| FR-P06 | Support partial capture | Medium |
| FR-P07 | Support void (cancel authorization) | High |
| FR-P08 | Support full refund | High |
| FR-P09 | Support partial refund | Medium |
| FR-P10 | Implement idempotency to prevent duplicate charges | High |
| FR-P11 | Auto-expire unpaid orders after 30 minutes | Medium |
| FR-P12 | Support 3D Secure (OTP) verification | Medium |
| FR-P13 | Track payment status through state machine | High |
| FR-P14 | Return standardized error codes | High |

### 4.3 Payment Routing

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-R01 | Route payments to optimal bank/acquirer | High |
| FR-R02 | Support cost-based routing (cheapest acquirer) | Medium |
| FR-R03 | Support success-rate-based routing | Medium |
| FR-R04 | Support card-type-based routing (Visa → Bank A, MC → Bank B) | Medium |
| FR-R05 | Implement failover (if Bank A fails, try Bank B) | High |
| FR-R06 | Communicate with banks via ISO 8583 protocol (TCP) | High |
| FR-R07 | AI-powered dynamic routing based on historical data | Medium |
| FR-R08 | Track success rates and latency per route | Medium |

### 4.4 Settlement

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-S01 | Run daily batch settlement (midnight) | High |
| FR-S02 | Calculate net settlement (payments - refunds - fees) | High |
| FR-S03 | Support T+1, T+2, T+3 settlement schedules | High |
| FR-S04 | Generate settlement reports (PDF/CSV) | Medium |
| FR-S05 | Support split settlement (marketplace model) | Low |
| FR-S06 | Reconciliation with bank records | Medium |
| FR-S07 | Handle failed settlements with retry | Medium |

### 4.5 Webhooks

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-W01 | Send webhook for payment events (authorized, captured, failed) | High |
| FR-W02 | Send webhook for refund events | High |
| FR-W03 | Send webhook for settlement events | Medium |
| FR-W04 | Sign webhooks with HMAC-SHA256 | High |
| FR-W05 | Retry failed webhooks with exponential backoff (5 attempts) | High |
| FR-W06 | Dead Letter Queue for permanently failed webhooks | Medium |
| FR-W07 | Webhook delivery logs accessible via API | Medium |

### 4.6 Notifications

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-N01 | Send payment confirmation email to customer | Medium |
| FR-N02 | Send payment confirmation SMS to customer | Low |
| FR-N03 | Send settlement notification to merchant | Medium |
| FR-N04 | Send fraud alert to operations team | High |
| FR-N05 | Send failed payment alert | Medium |

### 4.7 Fraud Detection (AI Feature)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-F01 | Rule-based fraud detection (velocity, amount, geo) | High |
| FR-F02 | Risk scoring for every transaction (0-100) | High |
| FR-F03 | Auto-approve low-risk transactions (<40) | High |
| FR-F04 | Trigger additional verification for medium-risk (40-70) | Medium |
| FR-F05 | Auto-decline very high-risk transactions (>90) | High |
| FR-F06 | Manual review queue for flagged transactions | Medium |
| FR-F07 | AI anomaly detection using transaction patterns | Medium |
| FR-F08 | Device fingerprinting | Low |

### 4.8 Merchant Dashboard (Frontend)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-D01 | Login/Register for merchant users | High |
| FR-D02 | Overview page with transaction stats and charts | High |
| FR-D03 | Transactions list with search, filter, pagination | High |
| FR-D04 | Transaction detail page | Medium |
| FR-D05 | Settlements page | Medium |
| FR-D06 | API keys management page | High |
| FR-D07 | Settings page (webhooks, payment methods) | Medium |
| FR-D08 | Analytics charts (volume, success rate, revenue) | Medium |

### 4.9 Hosted Checkout (Frontend)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-C01 | Hosted payment page accessible via URL | High |
| FR-C02 | Display payment amount and merchant name | High |
| FR-C03 | Card payment form with validation | High |
| FR-C04 | UPI payment flow (enter VPA) | High |
| FR-C05 | Net banking flow (select bank, redirect) | Medium |
| FR-C06 | 3D Secure OTP page | Medium |
| FR-C07 | Success/failure result pages | High |
| FR-C08 | Redirect back to merchant after completion | High |
| FR-C09 | Mobile responsive design | High |
| FR-C10 | Merchant branding (logo, colors) | Low |

---

## 5. Non-Functional Requirements

### 5.1 Performance

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-P01 | Payment authorization response time | <500ms (p95) |
| NFR-P02 | API response time (non-payment) | <200ms (p95) |
| NFR-P03 | Webhook delivery after event | <5 seconds |
| NFR-P04 | Throughput (design capacity) | 1000 TPS |
| NFR-P05 | Database query response | <50ms |

### 5.2 Availability & Reliability

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-A01 | System uptime | 99.9% |
| NFR-A02 | Zero data loss for payments | Guaranteed |
| NFR-A03 | Idempotent operations | All payment writes |
| NFR-A04 | Graceful degradation | If one service fails, others continue |
| NFR-A05 | Retry with circuit breaker | All inter-service calls |

### 5.3 Security

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-S01 | HTTPS/TLS for all external communication | Mandatory |
| NFR-S02 | Card numbers never stored (PCI-DSS principle) | Mandatory |
| NFR-S03 | Passwords hashed with BCrypt | Mandatory |
| NFR-S04 | JWT token-based authentication | All APIs |
| NFR-S05 | API key authentication for merchants | All payment APIs |
| NFR-S06 | Rate limiting at API gateway | 100 req/sec per merchant |
| NFR-S07 | Webhook signature verification (HMAC) | All webhooks |
| NFR-S08 | Input validation on all endpoints | Mandatory |
| NFR-S09 | Audit trail for all operations | Mandatory |

### 5.4 Scalability

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-SC01 | Horizontal scaling (add more instances) | Supported |
| NFR-SC02 | Database connection pooling | HikariCP |
| NFR-SC03 | Caching layer for frequent reads | Redis |
| NFR-SC04 | Async processing for non-critical ops | SQS queues |
| NFR-SC05 | Stateless services (no server-side sessions) | All services |

### 5.5 Observability

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-O01 | Structured logging (JSON format) | All services |
| NFR-O02 | Correlation ID across services | Every request |
| NFR-O03 | Health check endpoints | All services |
| NFR-O04 | Custom metrics (payment count, latency, errors) | CloudWatch |
| NFR-O05 | Alerting on failures | SNS notifications |

---

## 6. AI/ML Features Specification

### 6.1 AI Feature: Smart Fraud Detection

**What:** AI-powered system that analyzes every transaction in real-time and predicts
whether it's fraudulent.

**How it works:**
```
Transaction comes in → Extract features → Apply rules + ML model → Risk score → Decision
```

**Features extracted per transaction:**
- Transaction amount
- Time of day
- Customer's historical spending pattern
- Device fingerprint (new/known)
- Geographic location vs usual location
- Velocity (transactions per minute)
- Card BIN risk category
- Merchant category code risk

**ML Model:**
- Algorithm: Decision Tree / Random Forest (lightweight, fast inference)
- Training data: Simulated historical transactions (labeled fraud/legit)
- Inference time: <10ms
- Retrained: Weekly (or when fraud patterns change)

**AWS Integration:**
- Option A: Custom model in Java (decision tree) — no AWS AI service needed
- Option B: AWS Bedrock for complex pattern analysis (uses credits)
- Option C: AWS Comprehend for transaction description analysis

---

### 6.2 AI Feature: Smart Payment Routing

**What:** AI determines the best bank/acquirer for each payment to maximize
success rate and minimize cost.

**How it works:**
```
Payment request → Analyze features → Score each route → Pick best route
```

**Features considered:**
- Card type (Visa/MC/RuPay)
- Card issuing bank
- Transaction amount
- Time of day (bank performance varies by time)
- Historical success rate per route
- Current latency per bank
- Cost per route

**ML Model:**
- Algorithm: Multi-armed bandit (explore vs exploit)
- Learns over time which route works best for which transaction type
- Falls back to rule-based routing if insufficient data

---

### 6.3 AI Feature: Transaction Categorization

**What:** Automatically categorize transactions for merchant analytics.

**How it works:**
- Analyze merchant name/description
- Assign categories: Food, Shopping, Travel, Bills, Entertainment, etc.
- Power analytics dashboard charts

**AWS Integration:**
- AWS Comprehend (NLP) for text classification
- Or custom keyword-based classifier (zero cost)

---

### 6.4 AI Feature: Anomaly Detection (Spending Pattern)

**What:** Detect unusual spending patterns that may indicate compromised accounts.

**How it works:**
- Build profile of normal behavior per card/merchant
- Flag deviations (e.g., card usually used in Mumbai, suddenly used in Nigeria)
- Alert operations team

**Implementation:**
- Statistical approach: Z-score on transaction amount vs historical average
- Sliding window: Track last 30 days of spending
- Alert if current transaction > 3 standard deviations from mean

---

## 7. System Constraints

| Constraint | Detail |
|-----------|--------|
| Budget | AWS Free Tier only ($200 credits + always-free services) |
| Timeline | ~50 days of development |
| Team size | 1 developer (learning project) |
| Card data | Never stored (only passed through for authorization) |
| ISO 8583 | Simulated bank (not connected to real banking network) |
| Payment methods | Simulated (no real money movement) |
| KYC | Simplified (no real document verification) |

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

The project is considered complete when:

1. ✅ All 11 microservices are running and communicating
2. ✅ Payment can be created, authorized, captured, and settled end-to-end
3. ✅ ISO 8583 messages are sent to bank simulator and responses are parsed
4. ✅ Webhooks are delivered reliably with retry
5. ✅ Settlement batch runs daily and calculates correctly
6. ✅ Fraud detection scores every transaction
7. ✅ Merchant dashboard shows transactions, analytics, and settings
8. ✅ Hosted checkout accepts card/UPI/net banking
9. ✅ All services are containerized with Docker
10. ✅ CI/CD pipeline builds, tests, and pushes images
11. ✅ System is deployed on AWS (EC2, RDS, etc.)
12. ✅ Monitoring dashboards and alerts are configured
13. ✅ Swagger API documentation for every service
14. ✅ Unit and integration tests pass


---

## 11. API Documentation Requirements

### 11.1 Swagger / OpenAPI (Per Service)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-API01 | Every service exposes Swagger UI at /swagger-ui.html | High |
| FR-API02 | OpenAPI 3.0 JSON spec at /v3/api-docs | High |
| FR-API03 | All endpoints documented with description, request/response schema | High |
| FR-API04 | Authentication requirements documented per endpoint | High |
| FR-API05 | Error response schemas documented (4xx, 5xx) | Medium |
| FR-API06 | Example request/response in Swagger UI | Medium |
| FR-API07 | Aggregated Swagger at API Gateway (all services in one UI) | High |

### 11.2 Developer Portal

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-DEV01 | Hosted documentation site (like Stripe Docs) | Medium |
| FR-DEV02 | Getting Started guide (register → first payment in 5 mins) | Medium |
| FR-DEV03 | API Reference with all endpoints, parameters, responses | High |
| FR-DEV04 | Code examples in multiple languages (Java, Python, cURL) | Medium |
| FR-DEV05 | Webhook integration guide with signature verification code | Medium |
| FR-DEV06 | Test mode vs Live mode documentation | Medium |
| FR-DEV07 | Error codes reference table | Medium |
| FR-DEV08 | Changelog / API versioning documentation | Low |

### 11.3 Postman Collection

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-PM01 | Postman collection with all endpoints | High |
| FR-PM02 | Pre-configured environments (Local + AWS) | High |
| FR-PM03 | Pre-request scripts (auto-generate idempotency keys) | Medium |
| FR-PM04 | Test scripts (verify response codes, schemas) | Medium |
| FR-PM05 | Chained request flows (create order → pay → capture) | Medium |
| FR-PM06 | Collection exported and versioned in Git (docs/postman/) | Medium |
| FR-PM07 | Newman CLI integration for automated API testing | Low |

---

## 12. AI Features Summary

| # | Feature | Purpose | Algorithm | AWS Service |
|---|---------|---------|-----------|-------------|
| 1 | Smart Fraud Detection | Score transactions, prevent fraud | Rule engine + Decision Tree | Optional: Bedrock |
| 2 | Smart Payment Routing | Pick best bank for each payment | Multi-armed bandit | None (custom Java) |
| 3 | Transaction Categorization | Auto-tag for analytics | Keyword NLP | Optional: Comprehend |
| 4 | Anomaly Detection | Detect compromised cards | Z-score statistics | None (custom Java) |
| 5 | Predictive Analytics | Forecast volumes on dashboard | Moving average + trend | None (custom Java) |

---

## End of Requirements Document
