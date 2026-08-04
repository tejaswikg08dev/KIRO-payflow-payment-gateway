# PayFlow — Introduction

**Document Version:** 1.0  
**Last Updated:** August 2026  
**Author:** Tejaswi  

---

## 1. What is PayFlow?

PayFlow is a **production-ready Payment Gateway and Merchant Platform** — similar to Stripe, Razorpay, or PayPal.

### Simple Explanation

Imagine you run an online store. When a customer wants to buy something:

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│   Customer clicks "Pay ₹500"                                        │
│          │                                                           │
│          ▼                                                           │
│   ┌─────────────────────────────────────────────────────────┐       │
│   │                    PayFlow                                │       │
│   │                                                           │       │
│   │  1. Receives payment request                             │       │
│   │  2. Validates card/UPI details                           │       │
│   │  3. Checks for fraud                                     │       │
│   │  4. Sends to bank for approval                           │       │
│   │  5. Returns success/failure                              │       │
│   │  6. Notifies merchant                                    │       │
│   │  7. Settles money to merchant's bank                     │       │
│   └─────────────────────────────────────────────────────────┘       │
│          │                                                           │
│          ▼                                                           │
│   Customer sees "Payment Successful!"                               │
│   Merchant receives money in their bank account                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Technical Definition

PayFlow is a **microservices-based payment processing system** that:

- Accepts multiple payment methods (cards, UPI, net banking)
- Communicates with banks using **ISO 8583** protocol (same as Visa/Mastercard)
- Provides real-time fraud detection using **AI/ML**
- Delivers reliable webhook notifications to merchants
- Handles automated daily settlements
- Offers merchant dashboards for analytics and management

---

## 2. Why Build This Project?

### Learning Goals

By building PayFlow, you will learn:

| Category | Skills |
|----------|--------|
| **Backend** | Java 17, Spring Boot 3, Spring Cloud, Microservices |
| **Database** | PostgreSQL, Redis, DynamoDB, Database design |
| **Protocols** | ISO 8583 (bank communication), TCP/IP, REST APIs |
| **Security** | JWT, BCrypt, API Keys, HMAC signatures |
| **Frontend** | React 18, TypeScript, Tailwind CSS |
| **DevOps** | Docker, GitHub Actions CI/CD, AWS deployment |
| **AI/ML** | Fraud detection, Risk scoring, Smart routing |
| **Architecture** | Microservices patterns, Event-driven design |

### Real-World Value

This project teaches you how **actual payment systems work**:

- How your card payment reaches the bank
- How Stripe/Razorpay process millions of transactions
- Why payment systems need 99.9% uptime
- How fraud detection saves companies millions

### Interview Preparation

Every component maps to common interview topics:

| PayFlow Component | Interview Topic |
|-------------------|-----------------|
| JWT Authentication | "How do you secure APIs?" |
| Redis Caching | "How do you handle high traffic?" |
| ISO 8583 Protocol | "Have you worked with binary protocols?" |
| State Machine | "How do you handle complex workflows?" |
| Spring Batch | "How do you process large datasets?" |
| Circuit Breaker | "How do you handle service failures?" |

---

## 3. Project Scope

### What PayFlow Does (In Scope)

| Feature | Description |
|---------|-------------|
| ✅ Merchant Onboarding | Register, KYC, API keys |
| ✅ Payment Processing | Card, UPI, Net Banking |
| ✅ Bank Communication | ISO 8583 protocol (simulated) |
| ✅ Fraud Detection | Rule engine + ML scoring |
| ✅ Smart Routing | AI-powered bank selection |
| ✅ Webhooks | Reliable event delivery |
| ✅ Settlement | Daily batch processing |
| ✅ Notifications | Email/SMS via AWS SNS |
| ✅ Dashboard | React merchant portal |
| ✅ Checkout | Hosted payment page |
| ✅ API Documentation | Swagger + Developer Portal |
| ✅ Monitoring | CloudWatch dashboards |

### What PayFlow Doesn't Do (Out of Scope)

| Feature | Reason |
|---------|--------|
| ❌ Real bank integration | Requires licensing, compliance |
| ❌ Real money movement | Simulated for learning |
| ❌ PCI-DSS certification | Requires formal audit |
| ❌ Multi-currency | Complexity, focus on core |
| ❌ Recurring payments | Future enhancement |
| ❌ Mobile SDK | Focus on web APIs |

---

## 4. System Overview

### Microservices Architecture

PayFlow consists of **11 microservices**:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           PAYFLOW SYSTEM                                  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    INFRASTRUCTURE LAYER                             │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                │ │
│  │  │  Service    │  │   Config    │  │    API      │                │ │
│  │  │  Registry   │  │   Server    │  │   Gateway   │                │ │
│  │  │  (Eureka)   │  │             │  │             │                │ │
│  │  │   :8761     │  │   :8888     │  │   :8080     │                │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                     BUSINESS SERVICES                               │ │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐         │ │
│  │  │ Identity  │ │ Merchant  │ │  Payment  │ │  Routing  │         │ │
│  │  │ Service   │ │ Service   │ │  Service  │ │  Service  │         │ │
│  │  │  :8081    │ │  :8082    │ │  :8083    │ │  :8084    │         │ │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────┘         │ │
│  │                                                                    │ │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐         │ │
│  │  │Settlement │ │  Webhook  │ │Notification│ │   Bank    │         │ │
│  │  │ Service   │ │  Service  │ │  Service  │ │ Simulator │         │ │
│  │  │  :8085    │ │  :8086    │ │  :8087    │ │  :9000    │         │ │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────┘         │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                       DATA LAYER                                    │ │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────┐ ┌─────┐       │ │
│  │  │PostgreSQL │ │   Redis   │ │ DynamoDB  │ │ SQS │ │ SNS │       │ │
│  │  │  :5432    │ │  :6379    │ │  :8000    │ │     │ │     │       │ │
│  │  └───────────┘ └───────────┘ └───────────┘ └─────┘ └─────┘       │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

### Service Responsibilities

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | service-registry | 8761 | Service discovery (Eureka) |
| 2 | config-server | 8888 | Centralized configuration |
| 3 | api-gateway | 8080 | Single entry point, routing, rate limiting |
| 4 | identity-service | 8081 | User registration, login, JWT tokens |
| 5 | merchant-service | 8082 | Merchant onboarding, API keys, settings |
| 6 | payment-service | 8083 | Payment lifecycle, orders, refunds |
| 7 | routing-service | 8084 | Smart routing, ISO 8583, bank communication |
| 8 | settlement-service | 8085 | Daily batch settlement, fee calculation |
| 9 | webhook-service | 8086 | Event delivery, retry, DLQ |
| 10 | notification-service | 8087 | Email/SMS via SNS |
| 11 | bank-simulator | 9000 | Simulates Visa/MC bank responses |

---

## 5. Technology Stack Summary

### Backend
- **Java 17** — Core language
- **Spring Boot 3.2** — Application framework
- **Spring Cloud** — Microservices (Gateway, Eureka, Config, Feign)
- **Spring Security 6** — Authentication, authorization
- **Spring Batch 5** — Settlement batch processing

### Database & Messaging
- **PostgreSQL 15** — Relational data (ACID transactions)
- **Redis 7** — Caching, rate limiting, idempotency
- **DynamoDB** — Event logs, webhook delivery
- **SQS** — Async message queues
- **SNS** — Email/SMS notifications

### Frontend
- **React 18** — UI framework
- **TypeScript 5** — Type safety
- **Vite 5** — Build tool
- **Tailwind CSS 3** — Styling

### DevOps
- **Docker** — Containerization
- **GitHub Actions** — CI/CD pipelines
- **AWS Free Tier** — Cloud deployment

---

## 6. Sprint-Based Learning Approach

This project is organized into **12 sprints**, each delivering a complete vertical slice:

| Sprint | Feature | Duration |
|--------|---------|----------|
| 0 | Foundation & Setup | 1 week |
| 1 | Auth & Onboarding | 2 weeks |
| 2 | API Key Management | 2 weeks |
| 3 | Order Creation | 2 weeks |
| 4 | Card Payment | 2 weeks |
| 5 | Capture & Fraud | 2 weeks |
| 6 | Refunds & UPI | 2 weeks |
| 7 | Webhooks | 2 weeks |
| 8 | Settlement | 2 weeks |
| 9 | AI Features | 2 weeks |
| 10 | Developer Portal | 2 weeks |
| 11 | Monitoring | 2 weeks |
| 12 | Production | 2 weeks |

Each sprint covers:
- ✅ Backend implementation
- ✅ Frontend UI
- ✅ Database schema
- ✅ Docker setup
- ✅ CI/CD pipeline
- ✅ AWS deployment
- ✅ Testing
- ✅ Git workflow

---

## 7. How to Use This Documentation

### Reading Order

```
1. Start Here
   └── 00-project-overview/ (understand the project)
       └── 01-introduction.md ← YOU ARE HERE
       └── 02-tech-stack-explained.md
       └── ...

2. Setup Environment
   └── 01-environment-setup/ (install tools)
       └── 01-windows-setup.md
       └── 02-java-17-installation.md
       └── ...

3. Understand Design
   └── 02-master-documents/ (reference)
       └── requirements-complete.md
       └── hld-complete.md
       └── ...

4. Build Sprint by Sprint
   └── 03-sprints/
       └── sprint-00-foundation/
       └── sprint-01-auth-onboarding/
       └── ...

5. Reference as Needed
   └── 04-reference/
       └── glossary.md
       └── troubleshooting.md
       └── ...
```

### Document Types

| Type | Purpose | When to Use |
|------|---------|-------------|
| **Overview docs** | Understand the big picture | Read once at start |
| **Setup docs** | Install development environment | Follow once |
| **Master docs** | Complete design reference | Look up when needed |
| **Sprint docs** | Step-by-step implementation | Type along |
| **Reference docs** | Quick lookup | When stuck |

---

## 8. Prerequisites

### Required Knowledge

| Level | Topics |
|-------|--------|
| **Basic** | Java syntax, OOP concepts |
| **Basic** | HTML, CSS, JavaScript |
| **Basic** | SQL (SELECT, INSERT, UPDATE) |
| **Basic** | Git (clone, commit, push) |
| **Helpful** | Spring Boot basics |
| **Helpful** | React basics |

### Required Hardware

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| RAM | 8 GB | 16 GB |
| Storage | 50 GB free | 100 GB SSD |
| CPU | 4 cores | 8 cores |
| Internet | Stable connection | Stable connection |

### Required Software (Will Install)

- Java 17 (JDK)
- Maven 3.9+
- Node.js 18+
- Docker Desktop
- Git
- VS Code or IntelliJ IDEA
- Postman

---

## 9. Next Steps

**Continue to:** [02-tech-stack-explained.md](./02-tech-stack-explained.md)

This will explain every technology we use — what it is, why we chose it, and how it fits in PayFlow.

---

## 10. Quick Reference

### Important Links (After Setup)

| Service | Local URL |
|---------|-----------|
| Eureka Dashboard | http://localhost:8761 |
| API Gateway | http://localhost:8080 |
| Swagger (All APIs) | http://localhost:8080/swagger-ui.html |
| Merchant Dashboard | http://localhost:3000 |
| Hosted Checkout | http://localhost:3001 |
| Developer Portal | http://localhost:3002 |

### Key Contacts

- **Documentation Issues:** Create GitHub issue
- **Questions:** Add comments in PR

---

**End of Introduction**

*Next: [02-tech-stack-explained.md](./02-tech-stack-explained.md)*
