# PayFlow — Sprint Roadmap

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

PayFlow is built in **12 sprints**, each delivering a complete vertical slice (Backend + Frontend + DB + Docker + CI/CD + AWS + Testing).

---

## Sprint Summary

| Sprint | Name | Duration | Key Deliverable |
|--------|------|----------|-----------------|
| 0 | Foundation | 1 week | Dev environment ready |
| 1 | Auth & Onboarding | 2 weeks | User login, merchant registration |
| 2 | API Key Management | 2 weeks | API keys, webhook config |
| 3 | Order Creation | 2 weeks | Create payment orders |
| 4 | Card Payment | 2 weeks | Process card payments |
| 5 | Capture & Fraud | 2 weeks | Capture, void, fraud detection |
| 6 | Refunds & UPI | 2 weeks | Refunds, UPI payments |
| 7 | Webhooks | 2 weeks | Event notifications |
| 8 | Settlement | 2 weeks | Daily batch settlement |
| 9 | AI Features | 2 weeks | ML fraud, smart routing |
| 10 | Developer Portal | 2 weeks | API documentation site |
| 11 | Monitoring | 2 weeks | Logging, metrics, alerts |
| 12 | Production | 2 weeks | Full AWS deployment |

**Total Duration:** ~25 weeks (~6 months)

---

## Sprint Details

### Sprint 0: Foundation & Setup

**Duration:** 1 week  
**Goal:** Development environment ready, project initialized

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | Parent POM, common-lib |
| Database | PostgreSQL schemas |
| Infrastructure | Docker Compose (infra) |
| Git | Repository, branching strategy |

**Key Learning:**
- Multi-module Maven project
- Docker basics
- Git workflow

---

### Sprint 1: Auth & Onboarding

**Duration:** 2 weeks  
**Goal:** Users can register and login, merchants can onboard

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | service-registry, config-server, api-gateway, identity-service, merchant-service |
| Database | users, merchants tables |
| Frontend | Login, Register, Dashboard layout |
| Docker | Dockerfiles for 5 services |
| CI/CD | GitHub Actions backend pipeline |
| AWS | VPC, RDS (PostgreSQL) |
| Testing | Unit tests for auth |

**APIs Built:**
- POST /v1/auth/register
- POST /v1/auth/login
- POST /v1/merchants

**Key Learning:**
- Spring Cloud (Eureka, Config, Gateway)
- JWT authentication
- Spring Security
- React + TypeScript basics

---

### Sprint 2: API Key Management

**Duration:** 2 weeks  
**Goal:** Merchants can generate API keys and configure settings

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | API key generation, webhook config |
| Database | api_keys, merchant_settings tables |
| Frontend | API Keys page, Settings page |
| CI/CD | Postman tests in pipeline |
| AWS | Deploy services to EC2 |

**APIs Built:**
- POST /v1/merchants/{id}/api-keys
- DELETE /v1/merchants/{id}/api-keys/{keyId}
- PUT /v1/merchants/{id}/webhook
- PUT /v1/merchants/{id}/settings

**Key Learning:**
- SHA-256 hashing
- API key authentication filter
- React forms and state

---

### Sprint 3: Order Creation

**Duration:** 2 weeks  
**Goal:** Create payment orders, state machine, idempotency

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | payment-service, order creation, state machine |
| Database | orders, payments tables |
| Frontend | Hosted Checkout (order page) |
| Infrastructure | Redis for idempotency |
| AWS | ElastiCache (Redis) |

**APIs Built:**
- POST /v1/orders
- GET /v1/orders/{id}

**Key Learning:**
- State machine pattern
- Redis idempotency
- Separate React apps (checkout vs dashboard)

---

### Sprint 4: Card Payment

**Duration:** 2 weeks  
**Goal:** Process card payments through bank (ISO 8583)

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | routing-service, bank-simulator, ISO 8583 |
| Protocol | ISO 8583 encoder/decoder, TCP client |
| Frontend | Card payment form |
| Docker | TCP server container |

**APIs Built:**
- POST /v1/payments (authorize)
- POST /internal/route

**Key Learning:**
- ISO 8583 protocol (deep dive)
- TCP/IP programming with Netty
- Credit card validation (Luhn algorithm)

---

### Sprint 5: Capture & Fraud Detection

**Duration:** 2 weeks  
**Goal:** Capture/void payments, detect fraud

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | Capture, void, fraud rule engine |
| AI | Risk scoring (0-100) |
| Frontend | Transaction list, fraud indicators |
| Resilience | Circuit breaker (Resilience4j) |
| AWS | CloudWatch alarms for fraud |

**APIs Built:**
- POST /v1/payments/{id}/capture
- POST /v1/payments/{id}/void

**Key Learning:**
- Fraud detection rules
- Circuit breaker pattern
- Transaction dashboards

---

### Sprint 6: Refunds & UPI

**Duration:** 2 weeks  
**Goal:** Process refunds and UPI payments

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | Refund logic, UPI payment flow |
| Protocol | ISO 8583 reversal (MTI 0400) |
| Frontend | Refund button, UPI form |

**APIs Built:**
- POST /v1/payments/{id}/refund

**Key Learning:**
- Full vs partial refunds
- UPI collect flow
- Multiple payment methods

---

### Sprint 7: Webhooks

**Duration:** 2 weeks  
**Goal:** Reliable event delivery to merchants

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | webhook-service, notification-service |
| Database | DynamoDB (webhook_events) |
| Messaging | SQS queues |
| Security | HMAC-SHA256 signatures |
| Frontend | Webhook logs page |

**APIs Built:**
- GET /v1/webhooks/events
- POST /v1/webhooks/events/{id}/retry

**Key Learning:**
- Event-driven architecture
- HMAC webhook signatures
- Exponential backoff retry
- Dead Letter Queue

---

### Sprint 8: Settlement

**Duration:** 2 weeks  
**Goal:** Daily batch settlement with fee calculation

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | settlement-service, Spring Batch job |
| Database | settlements, settlement_items tables |
| Scheduling | Daily cron job |
| Frontend | Settlements page, report download |

**APIs Built:**
- GET /v1/settlements
- GET /v1/settlements/{id}/report

**Key Learning:**
- Spring Batch (Reader, Processor, Writer)
- BigDecimal for money
- PDF/CSV report generation
- Cron scheduling

---

### Sprint 9: AI Features

**Duration:** 2 weeks  
**Goal:** ML-powered fraud detection and smart routing

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| AI | Decision tree model, training |
| Backend | ML inference, multi-armed bandit |
| Frontend | Fraud analytics, routing charts |

**Key Learning:**
- Decision tree basics
- Multi-armed bandit algorithm
- Z-score anomaly detection
- ML model deployment

---

### Sprint 10: Developer Portal

**Duration:** 2 weeks  
**Goal:** Stripe-like API documentation site

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | Swagger aggregation |
| Frontend | Developer Portal (API docs) |
| CI/CD | Frontend deployment to S3 |
| AWS | S3, CloudFront |

**Key Learning:**
- OpenAPI/Swagger
- API documentation best practices
- Static site hosting

---

### Sprint 11: Monitoring

**Duration:** 2 weeks  
**Goal:** Production-ready observability

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Backend | Actuator, structured logging |
| Metrics | Custom CloudWatch metrics |
| Alerting | SNS notifications |
| Security | OWASP/Trivy scanning |

**Key Learning:**
- Spring Actuator
- JSON logging
- CloudWatch dashboards
- Security scanning

---

### Sprint 12: Production Deployment

**Duration:** 2 weeks  
**Goal:** Full AWS deployment, demo-ready

**What You'll Build:**
| Layer | Deliverable |
|-------|-------------|
| Infrastructure | ALB, final security |
| CI/CD | Complete pipeline |
| Testing | Full E2E regression |
| Documentation | Runbooks, demo script |

**Key Learning:**
- Production checklist
- Load balancing
- Cost optimization
- Release management

---

## Visual Roadmap

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PayFlow Sprint Roadmap                              │
│                                                                              │
│  Week   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15          │
│        ┌───┬───────┬───────┬───────┬───────┬───────┬───────┬───────┐       │
│ Sprint │ 0 │   1   │   2   │   3   │   4   │   5   │   6   │   7   │       │
│        │   │       │       │       │       │       │       │       │       │
│        │Set│ Auth  │  API  │ Order │ Card  │Capture│Refund │Webhook│       │
│        │up │       │ Keys  │       │Payment│ Fraud │  UPI  │       │       │
│        └───┴───────┴───────┴───────┴───────┴───────┴───────┴───────┘       │
│                                                                              │
│  Week  16  17  18  19  20  21  22  23  24  25                               │
│        ┌───────┬───────┬───────┬───────┬───────┐                            │
│ Sprint │   8   │   9   │  10   │  11   │  12   │                            │
│        │       │       │       │       │       │                            │
│        │Settle │  AI   │  Dev  │Monitor│ Prod  │                            │
│        │ment   │Feature│Portal │ ing   │Deploy │                            │
│        └───────┴───────┴───────┴───────┴───────┘                            │
│                                                                              │
│  Legend:                                                                     │
│  ▓▓▓▓▓ = Intensive coding                                                  │
│  ░░░░░ = Testing & documentation                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Skills Progression

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Skills by Sprint                                    │
│                                                                              │
│  Sprint 0-1:  ████████░░░░░░░░░░░░  Java, Spring Boot, React basics        │
│  Sprint 2-3:  ████████████░░░░░░░░  Security, State Machine, Redis         │
│  Sprint 4-5:  ████████████████░░░░  ISO 8583, TCP, Fraud Rules            │
│  Sprint 6-7:  ████████████████████  Event-driven, DynamoDB, SQS           │
│  Sprint 8-9:  ████████████████████  Spring Batch, ML, Algorithms          │
│  Sprint 10-12:████████████████████  DevOps, AWS, Production               │
│                                                                              │
│  By the end:                                                                 │
│  ✓ Full-stack development                                                   │
│  ✓ Microservices architecture                                               │
│  ✓ Payment domain expertise                                                 │
│  ✓ Cloud deployment                                                         │
│  ✓ Interview-ready portfolio                                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## How to Start

1. **Read:** `00-project-overview/` (all 6 documents) ← YOU ARE HERE
2. **Setup:** `01-environment-setup/` (install tools)
3. **Review:** `02-master-documents/` (understand full design)
4. **Build:** `03-sprints/sprint-00-foundation/` (start coding!)

---

## Next Steps

**Continue to:** [../01-environment-setup/01-windows-setup.md](../01-environment-setup/01-windows-setup.md)

Time to set up your development environment!

---

**End of Sprint Roadmap**

*Next: Environment Setup*
