# Phase 1: Project Overview, Roadmap & Environment Setup

## Project: PayFlow Payment Gateway (Production-Ready Microservices)

**Version:** 1.0
**Date:** July 20, 2026
**Author:** Tejaswi
**Status:** 🔄 In Progress

---

## 1. What Are We Building?

A production-ready payment gateway (like Stripe/Razorpay) with:
- Accept payments from customers (cards, UPI, net banking)
- ISO 8583 binary protocol to communicate with banks (same as Visa/Mastercard)
- Smart AI-powered payment routing (picks best bank for each transaction)
- AI fraud detection (scores every transaction 0-100)
- Merchant onboarding with API key management
- Payment lifecycle: authorize → capture → settle → refund
- Daily batch settlement (calculate fees, pay merchants)
- Reliable webhook delivery (HMAC signed, retry with backoff)
- Merchant dashboard (React) with analytics
- Hosted checkout page (like Stripe Checkout)
- Deployed on AWS Free Tier

This is an end-to-end project covering: Domain Knowledge → System Design → HLD → LLD → Coding → Testing → Docker → CI/CD → AWS Deployment → Monitoring.

---

## 2. Complete Phase Roadmap

| Phase | Name | What You'll Learn | Documents | Status |
|---|---|---|---|---|
| **Phase 1** | Project Overview & Domain Knowledge | Payment domain, ISO 8583 protocol, architecture decisions, AWS plan, environment setup | 6 sub-parts | ✅ |
| **Phase 2** | System Design (HLD + LLD + DB + API) | Architecture diagrams, class design, database schema, API spec, message contracts | 6 sub-parts | ✅ |
| **Phase 3** | Infrastructure Services (Code Starts) | Parent POM, common-lib, Eureka, Config Server, API Gateway, Docker Compose | 5 sub-parts | ✅ |
| **Phase 4** | Identity Service | User registration, login, JWT tokens, Spring Security, Swagger | 4 sub-parts | ✅ |
| **Phase 5** | Merchant Service | Onboarding, API key generation, fee config, webhook URL | 5 sub-parts | ✅ |
| **Phase 6** | Payment Service (Core) | Orders, authorize, capture, void, refund, idempotency, state machine | 9 sub-parts | ✅ |
| **Phase 7** | Routing Service + ISO 8583 + Bank Simulator | Smart routing, ISO encoder/decoder, TCP client/server, bank rules | 9 sub-parts | ✅ |
| **Phase 8** | Settlement Service | Spring Batch, fee calculation, daily cron, merchant payout | 6 sub-parts | 🔄 |
| **Phase 9** | Webhook Service | SQS listener, HMAC signing, retry logic, DLQ, delivery logs | 6 sub-parts | 🔄 |
| **Phase 10** | Notification + Fraud Detection | AWS SNS email/SMS, rule engine, ML scoring, anomaly detection | 5 sub-parts | 🔄 |
| **Phase 11** | React Frontend | Merchant dashboard, hosted checkout, developer portal | 8 sub-parts | 🔄 |
| **Phase 12** | Testing | JUnit 5, Mockito, TestContainers, REST Assured, coverage | 4 sub-parts | ⬜ |
| **Phase 13** | Docker Containerization | Multi-stage Dockerfiles, full docker-compose, networking | 4 sub-parts | ⬜ |
| **Phase 14** | CI/CD Pipeline | GitHub Actions, build+test+push, deployment automation | 4 sub-parts | ⬜ |
| **Phase 15** | AWS Deployment | VPC, RDS, ElastiCache, EC2, ALB, S3, CloudFront | 8 sub-parts | ⬜ |
| **Phase 16** | Monitoring & Observability | Actuator, structured logging, CloudWatch dashboards, alarms | 3 sub-parts | ⬜ |

**Total:** 16 phases, 87 sub-part documents

---

## 3. Tech Stack Summary

| Category | Technology | Version | Purpose |
|---|---|---|---|
| **Language** | Java | 17 (LTS) | Backend microservices |
| **Framework** | Spring Boot | 3.2.5 | REST APIs, security, data access, batch |
| **Spring Cloud** | Gateway, Eureka, Config, Feign | 2023.0.1 | Microservices infrastructure |
| **Build Tool** | Maven | 3.9.x | Multi-module build, dependency management |
| **Frontend** | React + TypeScript | 18.x + 5.x | Merchant dashboard, hosted checkout |
| **Frontend Build** | Vite | 5.x | Fast bundler |
| **CSS** | Tailwind CSS | 3.x | Utility-first styling |
| **SQL Database** | PostgreSQL | 15 | Users, merchants, payments, settlements (ACID) |
| **NoSQL Database** | AWS DynamoDB | Managed | Webhook events, routing metrics, audit trail |
| **Cache** | Redis | 7.x | Idempotency keys, rate limiting, caching |
| **Message Queue** | AWS SQS | Managed | Async payment events, webhook delivery |
| **Notifications** | AWS SNS | Managed | Email/SMS alerts |
| **Protocol** | ISO 8583 (custom) | — | Binary financial messages to bank simulator |
| **TCP** | Netty | 4.1.x | Bank communication (TCP sockets) |
| **Auth** | Spring Security + JWT | 6.x | Authentication & authorization |
| **API Docs** | SpringDoc OpenAPI | 2.3.x | Swagger UI per service |
| **Resilience** | Resilience4j | 2.x | Circuit breaker, retry, rate limiter |
| **Batch** | Spring Batch | 5.x | Settlement batch processing |
| **DB Migrations** | Flyway | 9.x | Version-controlled schema changes |
| **Mapping** | MapStruct | 1.5.x | DTO ↔ Entity conversion |
| **Containerization** | Docker + Compose | 24.x | Local dev + deployment |
| **CI/CD** | GitHub Actions | — | Automated build, test, deploy |
| **Cloud** | AWS Free Tier | — | Production deployment ($200 credits) |
| **Monitoring** | CloudWatch + Actuator | — | Metrics, logs, alarms |
| **Testing** | JUnit 5, Mockito, TestContainers | — | Unit + integration tests |
| **AI/ML** | Custom rule engine + Decision Tree | — | Fraud detection + smart routing |

---

## 4. Payment Domain Concepts Covered

| Concept | Where in Project | Phase |
|---|---|---|
| Payment authorization flow | Payment Service → Routing → Bank | 6, 7 |
| ISO 8583 protocol | Routing Service + Bank Simulator | 7 |
| Idempotency (prevent double charges) | Payment Service (Redis) | 6 |
| Payment state machine | Payment Service (states + transitions) | 6 |
| Settlement & reconciliation | Settlement Service (Spring Batch) | 8 |
| MDR fee calculation | Settlement Service | 8 |
| Webhook delivery (reliable) | Webhook Service (SQS + retry) | 9 |
| HMAC-SHA256 signing | Webhook Service | 9 |
| Fraud detection | Payment Service + Rule Engine | 10 |
| Smart payment routing | Routing Service (multi-armed bandit) | 7 |
| API key management | Merchant Service | 5 |
| PCI-DSS compliance patterns | Architecture (never store card numbers) | 2 |

---

## 5. Microservices Architecture

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | service-registry | 8761 | Eureka — service discovery |
| 2 | config-server | 8888 | Centralized configuration |
| 3 | api-gateway | 8080 | Routing, rate limiting, auth |
| 4 | identity-service | 8081 | Registration, login, JWT |
| 5 | merchant-service | 8082 | Onboarding, API keys |
| 6 | payment-service | 8083 | Payment lifecycle |
| 7 | routing-service | 8084 | Smart routing + ISO 8583 |
| 8 | settlement-service | 8085 | Batch settlement |
| 9 | webhook-service | 8086 | Event delivery |
| 10 | notification-service | 8087 | Email/SMS via SNS |
| 11 | bank-simulator | 9000 | ISO 8583 TCP bank mock |

---

## 6. Document Index (All Sub-Parts)

### Phase 1 — Foundation & Domain Knowledge (No Code)

| Document | Content |
|----------|---------|
| `phase1-project-overview.md` | **This file** — roadmap, tech stack, structure |
| `phase1-part1-payment-domain-knowledge.md` | How payments work, lifecycle, MDR, glossary |
| `phase1-part2-iso8583-protocol-deep-dive.md` | MTI, bitmap, fields, encoding, response codes |
| `phase1-part3-architecture-and-design-decisions.md` | Why microservices, patterns, resilience |
| `phase1-part4-aws-free-tier-plan.md` | New AWS tiers, costs, budget strategy |
| `phase1-part5-development-environment-setup.md` | Install Java, Maven, Docker, Node, Git |
| `phase1-part6-project-structure-and-git-setup.md` | Folder structure, Git init |

### Phase 2 — System Design (No Code)

| Document | Content |
|----------|---------|
| `phase2-part1-high-level-design.md` | Architecture diagrams, data flows, scaling |
| `phase2-part2-low-level-design.md` | Class diagrams, interfaces, patterns |
| `phase2-part3-database-schema-design.md` | All tables (SQL DDL), DynamoDB, indexes |
| `phase2-part4-api-specification.md` | Every endpoint with request/response examples |
| `phase2-part5-iso8583-message-specification.md` | Wire format, field specs, test cards |
| `phase2-part6-event-and-message-contracts.md` | SQS messages, webhook payloads |

### Phase 3 — Infrastructure Services (Code Starts)

| Document | Content |
|----------|---------|
| `phase3-part1-parent-pom-and-common-lib.md` | Maven multi-module, shared library code |
| `phase3-part2-service-registry-eureka.md` | Eureka server setup + verification |
| `phase3-part3-config-server.md` | Centralized config + service YAML files |
| `phase3-part4-api-gateway.md` | Routing, rate limiting, correlation ID |
| `phase3-part5-docker-compose-infrastructure.md` | PostgreSQL, Redis, DynamoDB, LocalStack |

### Phase 4 — Identity Service

| Document | Content |
|----------|---------|
| `phase4-part1-project-setup-and-database.md` | pom.xml, Flyway, User entity, application.yml |
| `phase4-part2-jwt-and-authentication.md` | JwtService, AuthService, register/login logic |
| `phase4-part3-controllers-and-security.md` | AuthController, SecurityConfig, endpoints |
| `phase4-part4-swagger-and-testing.md` | OpenAPI annotations, curl tests, Postman |

### Phase 5 — Merchant Service

| Document | Content |
|----------|---------|
| `phase5-part1-project-setup-and-database.md` | pom.xml, Flyway, Merchant/ApiKey entities |
| `phase5-part2-merchant-onboarding.md` | MerchantService, registration logic |
| `phase5-part3-api-key-management.md` | Key generation, SHA-256 hashing, validation |
| `phase5-part4-configuration-and-webhooks.md` | Fee config, webhook secret, settlement schedule |
| `phase5-part5-controllers-swagger-testing.md` | MerchantController, endpoints, curl tests |

### Phase 6 — Payment Service (Core)

| Document | Content |
|----------|---------|
| `phase6-part1-project-setup-and-database.md` | pom.xml, Flyway, Payment/Order entities |
| `phase6-part2-payment-state-machine.md` | States, transitions, validation |
| `phase6-part3-order-creation.md` | OrderService, POST /v1/orders |
| `phase6-part4-payment-authorization.md` | Card/UPI authorization flow |
| `phase6-part5-capture-and-void.md` | POST /capture, POST /void logic |
| `phase6-part6-refund.md` | RefundService, POST /refund |
| `phase6-part7-idempotency.md` | Redis-based IdempotencyService |
| `phase6-part8-feign-clients-and-integration.md` | Calls to routing + fraud services |
| `phase6-part9-controllers-swagger-testing.md` | PaymentController, full endpoint tests |

### Phase 7 — Routing Service + ISO 8583 + Bank Simulator

| Document | Content |
|----------|---------|
| `phase7-part1-routing-service-setup.md` | pom.xml, configuration |
| `phase7-part2-smart-routing-engine.md` | Strategy pattern, route scoring |
| `phase7-part3-failover-handling.md` | Circuit breaker, retry, fallback |
| `phase7-part4-iso8583-message-classes.md` | Iso8583Message, FieldDefinition, FieldType |
| `phase7-part5-iso8583-encoder-decoder.md` | Encoder (Java→bytes), Decoder (bytes→Java) |
| `phase7-part6-tcp-client-bank-communication.md` | BankTcpClient, socket I/O |
| `phase7-part7-bank-simulator-tcp-server.md` | TcpServer, request handler, rules |
| `phase7-part8-end-to-end-integration.md` | Full flow test: payment → routing → bank |
| `phase7-part9-controllers-swagger-testing.md` | RoutingController, curl tests |

### Phase 8 — Settlement Service

| Document | Content |
|----------|---------|
| `phase8-part1-project-setup-and-database.md` | pom.xml, Flyway, entities |
| `phase8-part2-fee-calculation.md` | FeeCalculator (MDR, GST, split) |
| `phase8-part3-spring-batch-settlement-job.md` | Reader, Processor, Writer, Job config |
| `phase8-part4-scheduler-and-payout.md` | @Scheduled cron, PayoutService |
| `phase8-part5-reconciliation-and-reports.md` | Report generation (PDF/CSV) |
| `phase8-part6-controllers-swagger-testing.md` | SettlementController, tests |

### Phase 9 — Webhook Service

| Document | Content |
|----------|---------|
| `phase9-part1-project-setup-and-dynamodb.md` | pom.xml, DynamoDB table creation |
| `phase9-part2-event-types-and-schema.md` | Event definitions, JSON payloads |
| `phase9-part3-hmac-signature-and-delivery.md` | SignatureGenerator, WebhookDispatcher |
| `phase9-part4-retry-and-dead-letter-queue.md` | RetryScheduler, DLQ handling |
| `phase9-part5-sqs-listener-and-integration.md` | SqsEventListener, message processing |
| `phase9-part6-controllers-swagger-testing.md` | Delivery logs API, manual retry |

### Phase 10 — Notification + Fraud Detection

| Document | Content |
|----------|---------|
| `phase10-part1-notification-service-setup.md` | pom.xml, SQS listener |
| `phase10-part2-sns-email-sms-integration.md` | AWS SNS for email/SMS |
| `phase10-part3-fraud-detection-rule-engine.md` | Rules (velocity, amount, geo, device) |
| `phase10-part4-ai-risk-scoring-ml-model.md` | Decision tree, scoring 0-100 |
| `phase10-part5-integration-and-testing.md` | Integration with payment-service |

### Phase 11 — React Frontend

| Document | Content |
|----------|---------|
| `phase11-part1-merchant-dashboard-setup.md` | Vite + React + TS + Tailwind |
| `phase11-part2-authentication-pages.md` | Login, Register components |
| `phase11-part3-dashboard-layout-and-overview.md` | Layout, sidebar, stats, charts |
| `phase11-part4-transactions-and-settlements.md` | Transaction list, settlement page |
| `phase11-part5-api-keys-and-settings.md` | API keys page, webhook config |
| `phase11-part6-hosted-checkout-setup.md` | Separate app, payment page |
| `phase11-part7-checkout-payment-forms.md` | Card form, UPI, net banking |
| `phase11-part8-checkout-success-failure.md` | Result pages, redirect |

### Phase 12 — Testing

| Document | Content |
|----------|---------|
| `phase12-part1-testing-strategy-and-setup.md` | Test pyramid, framework setup |
| `phase12-part2-unit-tests-service-layer.md` | JUnit 5 + Mockito examples |
| `phase12-part3-integration-tests.md` | TestContainers, full flow |
| `phase12-part4-api-tests-and-coverage.md` | REST Assured, JaCoCo |

### Phase 13 — Docker Containerization

| Document | Content |
|----------|---------|
| `phase13-part1-dockerfiles-java-services.md` | Multi-stage builds |
| `phase13-part2-dockerfiles-react-frontends.md` | Node build + Nginx |
| `phase13-part3-docker-compose-full-stack.md` | All services together |
| `phase13-part4-networking-and-optimization.md` | Networks, health checks |

### Phase 14 — CI/CD Pipeline

| Document | Content |
|----------|---------|
| `phase14-part1-github-actions-basics.md` | Workflows, jobs, steps |
| `phase14-part2-backend-pipeline.md` | Build, test, Docker push |
| `phase14-part3-frontend-pipeline.md` | Build, S3 deploy |
| `phase14-part4-deployment-automation.md` | EC2 deploy, secrets |

### Phase 15 — AWS Deployment

| Document | Content |
|----------|---------|
| `phase15-part1-aws-account-and-free-tier.md` | Account setup, credits |
| `phase15-part2-vpc-subnets-security-groups.md` | Networking |
| `phase15-part3-rds-elasticache-dynamodb.md` | Database services |
| `phase15-part4-ec2-docker-deployment.md` | EC2 + Docker |
| `phase15-part5-alb-and-routing.md` | Load balancer |
| `phase15-part6-s3-cloudfront-frontend.md` | Frontend hosting |
| `phase15-part7-end-to-end-verification.md` | Full system test |
| `phase15-part8-teardown-guide.md` | Cleanup, save credits |

### Phase 16 — Monitoring & Observability

| Document | Content |
|----------|---------|
| `phase16-part1-spring-boot-actuator.md` | Health, metrics, info |
| `phase16-part2-structured-logging.md` | JSON logs, correlation IDs |
| `phase16-part3-cloudwatch-dashboards-alarms.md` | Dashboards, alerting |

---

## 7. Additional Documents

| Document | Content |
|----------|---------|
| `requirements-document.md` | Full PRD (90+ requirements, AI features) |
| `design-document.md` | Architecture, ER diagrams, flows, security |

---

## 8. How to Follow This Guide

1. **Phase 1:** Read all 6 parts (domain knowledge, no code)
2. **Phase 2:** Read all 6 parts (design everything before coding)
3. **Phase 3 onwards:** Code step by step, follow each sub-part in order
4. Each sub-part has: **Goal → Prerequisites → Concept → Step-by-step code → Verification → Git commit**
5. **After each phase:** Run locally with Docker Compose and verify
6. **Phase 15:** Deploy to AWS

---

## 9. How to Run the Project (After All Phases)

```cmd
# 1. Start infrastructure (PostgreSQL, Redis, DynamoDB, LocalStack)
docker compose -f docker-compose-infra.yml up -d

# 2. Build all Java modules
mvn clean install -DskipTests

# 3. Start services (each in separate terminal):
cd service-registry && mvn spring-boot:run      # Port 8761
cd config-server && mvn spring-boot:run         # Port 8888
cd api-gateway && mvn spring-boot:run           # Port 8080
cd bank-simulator && mvn spring-boot:run        # Port 9000
cd identity-service && mvn spring-boot:run      # Port 8081
cd merchant-service && mvn spring-boot:run      # Port 8082
cd payment-service && mvn spring-boot:run       # Port 8083
cd routing-service && mvn spring-boot:run       # Port 8084
cd settlement-service && mvn spring-boot:run    # Port 8085
cd webhook-service && mvn spring-boot:run       # Port 8086
cd notification-service && mvn spring-boot:run  # Port 8087

# 4. Access:
# Eureka Dashboard:   http://localhost:8761
# API Gateway:        http://localhost:8080
# Swagger (Identity): http://localhost:8081/swagger-ui.html
# Swagger (Payment):  http://localhost:8083/swagger-ui.html
# Merchant Dashboard: http://localhost:3000
# Hosted Checkout:    http://localhost:3001
```

---

## Next Step

→ Start with **`phase1-part1-payment-domain-knowledge.md`**
