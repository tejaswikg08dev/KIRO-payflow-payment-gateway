# PayFlow — Payment Gateway & Merchant Platform

> A production-ready, AI-powered payment gateway system built with Java 17 Spring Boot
> microservices, ISO 8583 bank communication protocol, and deployed on AWS Free Tier.

---

## What Is PayFlow?

PayFlow is a **Stripe/Razorpay-like** payment gateway platform that enables merchants
(businesses) to accept digital payments from their customers. It handles the complete
payment lifecycle — from accepting payments to settling money into merchant accounts.

**Key Highlights:**
- 11 microservices (multi-module Maven project)
- ISO 8583 protocol for bank communication (same as Visa/Mastercard)
- AI-powered fraud detection and smart payment routing
- Swagger/OpenAPI documentation for every service
- Developer Portal with API reference (like Stripe Docs)
- Postman collections for testing
- Deployed on AWS Free Tier

---

## Tech Stack

### Backend

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 17 (LTS) | Core language |
| Spring Boot | 3.2.x | Application framework |
| Spring Cloud | 2023.x | Microservices infrastructure (Gateway, Eureka, Config, Feign) |
| Spring Security | 6.x | JWT authentication, API key auth, role-based access |
| Spring Data JPA | 3.2.x | PostgreSQL ORM |
| Spring Data Redis | 3.2.x | Cache, idempotency, rate limiting |
| Spring Batch | 5.x | Settlement batch processing |
| Resilience4j | 2.x | Circuit breaker, retry, rate limiter |
| Netty | 4.1.x | TCP client/server for ISO 8583 |
| Flyway | 9.x | Database version control (migrations) |
| MapStruct | 1.5.x | DTO-Entity mapping (compile-time) |
| Lombok | 1.18.x | Boilerplate reduction |

### API Documentation & Testing

| Technology | Version | Purpose |
|-----------|---------|---------|
| SpringDoc OpenAPI | 2.3.x | Auto-generated Swagger UI per service |
| Swagger UI | - | Interactive API explorer (try endpoints in browser) |
| OpenAPI 3.0 Spec | - | Machine-readable API specification (JSON/YAML) |
| Postman | - | API testing collections (test + live environments) |
| Developer Portal | React | Stripe-like API reference documentation site |

### Database & Messaging

| Technology | Version | Purpose |
|-----------|---------|---------|
| PostgreSQL | 15 | Relational data (users, payments, settlements) — ACID |
| Redis | 7 | Caching, idempotency keys, rate limiting, sessions |
| DynamoDB | - | Event logs, webhook delivery records, audit trail |
| Amazon SQS | - | Async message queues (payment events, notifications) |
| Amazon SNS | - | Email/SMS notifications |

### Protocol

| Technology | Purpose |
|-----------|---------|
| ISO 8583 | Binary financial message protocol (bank communication) |
| TCP/IP (Netty) | Transport layer for ISO 8583 messages |
| REST/HTTP | Service-to-service and external API communication |
| HMAC-SHA256 | Webhook signature verification |

### Frontend

| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 18.x | UI framework |
| TypeScript | 5.x | Type safety |
| Vite | 5.x | Build tool (fast) |
| Tailwind CSS | 3.x | Utility-first styling |
| TanStack Query | 5.x | API state management & caching |
| React Router | 6.x | Page routing |
| Recharts | 2.x | Charts for analytics dashboard |
| React Hook Form + Zod | - | Form handling + validation |

### AI/ML Features

| Technology | Purpose |
|-----------|---------|
| Custom Rule Engine (Java) | Fraud detection rules (velocity, amount, geo, device) |
| Decision Tree / Random Forest (Weka/Smile) | ML-based fraud risk scoring |
| Multi-Armed Bandit Algorithm | Smart payment routing (learn optimal routes) |
| Statistical Anomaly Detection (Z-score) | Detect unusual spending patterns |
| AWS Comprehend (optional) | Transaction text categorization |
| AWS Bedrock (optional) | AI-powered transaction insights |

### Infrastructure & DevOps

| Technology | Purpose |
|-----------|---------|
| Docker | Container per service |
| Docker Compose | Local multi-service orchestration |
| AWS EC2 (t3.micro) | Compute (backend services) |
| AWS RDS (db.t3.micro) | Managed PostgreSQL |
| AWS ElastiCache (cache.t3.micro) | Managed Redis |
| AWS DynamoDB | Always-free NoSQL |
| AWS SQS + SNS | Always-free messaging |
| AWS S3 + CloudFront | Frontend hosting (always-free) |
| AWS ALB | Load balancer |
| AWS ECR | Docker image registry |
| AWS CloudWatch | Monitoring, logs, alarms |
| GitHub Actions | CI/CD pipelines |
| Nginx | Reverse proxy on EC2 |

### Testing

| Technology | Purpose |
|-----------|---------|
| JUnit 5 | Unit testing |
| Mockito | Mocking in unit tests |
| TestContainers | Integration testing (real DB containers) |
| REST Assured | API endpoint testing |
| JaCoCo | Code coverage reports |

---

## Microservices Architecture

| # | Service | Port | Database | Responsibility |
|---|---------|------|----------|---------------|
| 1 | **service-registry** | 8761 | None | Eureka — service discovery |
| 2 | **config-server** | 8888 | Git/Local files | Centralized configuration |
| 3 | **api-gateway** | 8080 | Redis | Routing, rate limiting, auth, Swagger aggregation |
| 4 | **identity-service** | 8081 | PostgreSQL | Registration, login, JWT, roles |
| 5 | **merchant-service** | 8082 | PostgreSQL | Onboarding, API keys, fee config |
| 6 | **payment-service** | 8083 | PostgreSQL + Redis | Payment lifecycle (order → auth → capture → refund) |
| 7 | **routing-service** | 8084 | DynamoDB + Redis | Smart routing + ISO 8583 bank communication |
| 8 | **settlement-service** | 8085 | PostgreSQL | Batch settlement, fees, payouts |
| 9 | **webhook-service** | 8086 | DynamoDB + SQS | Reliable event delivery to merchants |
| 10 | **notification-service** | 8087 | SQS + SNS | Email/SMS alerts |
| 11 | **bank-simulator** | 9000 | In-memory | ISO 8583 TCP bank mock (Visa/MC/UPI) |

---

## AI Features

| # | Feature | What It Does | Implementation |
|---|---------|-------------|----------------|
| 1 | **Smart Fraud Detection** | Scores every transaction 0-100, auto approve/decline | Rule engine + ML decision tree |
| 2 | **Smart Payment Routing** | Picks best bank for each payment (max success, min cost) | Multi-armed bandit algorithm |
| 3 | **Transaction Categorization** | Auto-tag transactions (food, travel, shopping) | NLP / keyword classifier |
| 4 | **Anomaly Detection** | Flag unusual spending patterns | Z-score statistical analysis |
| 5 | **Predictive Analytics** | Forecast payment volumes, revenue | Time-series analysis |

---

## API Documentation Strategy

| Layer | Tool | Access URL |
|-------|------|-----------|
| Per-Service Swagger | SpringDoc OpenAPI | `http://localhost:{port}/swagger-ui.html` |
| Aggregated Gateway Swagger | Spring Cloud Gateway + SpringDoc | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON Spec | Auto-generated | `http://localhost:{port}/v3/api-docs` |
| Postman Collection | Exported from Swagger | `docs/postman/` folder |
| Developer Portal | React static site | `http://localhost:3002` (or S3 hosted) |

---

## Project Phases & Documentation

All documentation is in `docs/` folder. Follow Phase 1 → Phase 16 in order.

### Phase 1 — Foundation & Domain Knowledge (No Code)

| Part | Document | Content |
|------|----------|---------|
| Overview | `phase1-project-overview.md` | Project introduction, microservices table, phase structure |
| Part 1 | `phase1-part1-payment-domain-knowledge.md` | How payments work, lifecycle, MDR, settlement, glossary |
| Part 2 | `phase1-part2-iso8583-protocol-deep-dive.md` | MTI, bitmap, fields, encoding, response codes |
| Part 3 | `phase1-part3-architecture-and-design-decisions.md` | Why microservices, DB per service, security, caching |
| Part 4 | `phase1-part4-aws-free-tier-plan.md` | New AWS tiers, always-free services, $200 credits strategy |
| Part 5 | `phase1-part5-development-environment-setup.md` | Install Java 17, Maven, Docker, Node, Git, IDE, Postman |
| Part 6 | `phase1-part6-project-structure-and-git-setup.md` | Folder structure, parent POM, Git init |

### Phase 2 — System Design (No Code)

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase2-part1-high-level-design.md` | Architecture diagrams, component interaction, data flow |
| Part 2 | `phase2-part2-low-level-design.md` | Class diagrams, interfaces, design patterns per service |
| Part 3 | `phase2-part3-database-schema-design.md` | ER diagram, all tables, indexes, relationships |
| Part 4 | `phase2-part4-api-specification.md` | Every REST endpoint (method, path, request, response) |
| Part 5 | `phase2-part5-iso8583-message-specification.md` | Message formats, field packing rules |
| Part 6 | `phase2-part6-event-and-message-contracts.md` | SQS messages, webhook events, domain events |

### Phase 3 — Infrastructure Services (Code Starts)

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase3-part1-parent-pom-and-common-lib.md` | Multi-module Maven, shared DTOs, exceptions, utils |
| Part 2 | `phase3-part2-service-registry-eureka.md` | Eureka server, service registration |
| Part 3 | `phase3-part3-config-server.md` | Centralized YAML configs, Git-backed |
| Part 4 | `phase3-part4-api-gateway.md` | Routing, rate limiting, auth filter, Swagger aggregation |
| Part 5 | `phase3-part5-docker-compose-infrastructure.md` | PostgreSQL, Redis, DynamoDB Local, LocalStack |

### Phase 4 — Identity Service

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase4-part1-project-setup-and-database.md` | Dependencies, Flyway, entity models |
| Part 2 | `phase4-part2-jwt-and-authentication.md` | JWT generation/validation, login/register logic |
| Part 3 | `phase4-part3-controllers-and-security.md` | REST endpoints, Spring Security config |
| Part 4 | `phase4-part4-swagger-and-testing.md` | OpenAPI annotations, Swagger UI, unit tests |

### Phase 5 — Merchant Service

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase5-part1-project-setup-and-database.md` | Dependencies, schema, entities |
| Part 2 | `phase5-part2-merchant-onboarding.md` | Registration, KYC, business verification |
| Part 3 | `phase5-part3-api-key-management.md` | Generate test/live keys, authentication filter |
| Part 4 | `phase5-part4-configuration-and-webhooks.md` | Fee config, settlement schedule, webhook URL |
| Part 5 | `phase5-part5-controllers-swagger-testing.md` | Endpoints, OpenAPI docs, Postman, tests |

### Phase 6 — Payment Service (Core)

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase6-part1-project-setup-and-database.md` | Dependencies, schema, entities |
| Part 2 | `phase6-part2-payment-state-machine.md` | States, transitions, implementation |
| Part 3 | `phase6-part3-order-creation.md` | POST /v1/orders API |
| Part 4 | `phase6-part4-payment-authorization.md` | Card/UPI/NetBanking auth flow |
| Part 5 | `phase6-part5-capture-and-void.md` | Full/partial capture, void |
| Part 6 | `phase6-part6-refund.md` | Full/partial refund |
| Part 7 | `phase6-part7-idempotency.md` | Redis-based duplicate prevention |
| Part 8 | `phase6-part8-feign-clients-and-integration.md` | Calls to routing + fraud services |
| Part 9 | `phase6-part9-controllers-swagger-testing.md` | Endpoints, OpenAPI, Postman, tests |

### Phase 7 — Routing Service + ISO 8583 + Bank Simulator

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase7-part1-routing-service-setup.md` | Dependencies, configuration |
| Part 2 | `phase7-part2-smart-routing-engine.md` | Strategy pattern, cost/success routing |
| Part 3 | `phase7-part3-failover-handling.md` | Circuit breaker, retry, bank failover |
| Part 4 | `phase7-part4-iso8583-message-classes.md` | Java classes for messages |
| Part 5 | `phase7-part5-iso8583-encoder-decoder.md` | Build/parse binary messages |
| Part 6 | `phase7-part6-tcp-client-bank-communication.md` | Netty TCP client |
| Part 7 | `phase7-part7-bank-simulator-tcp-server.md` | TCP server, response rules |
| Part 8 | `phase7-part8-end-to-end-integration.md` | Full flow: Payment → Routing → ISO → Bank |
| Part 9 | `phase7-part9-controllers-swagger-testing.md` | Endpoints, OpenAPI, tests |

### Phase 8 — Settlement Service

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase8-part1-project-setup-and-database.md` | Dependencies, schema |
| Part 2 | `phase8-part2-fee-calculation.md` | MDR, fixed fees, split settlement |
| Part 3 | `phase8-part3-spring-batch-settlement-job.md` | Reader, processor, writer config |
| Part 4 | `phase8-part4-scheduler-and-payout.md` | Cron job, payout initiation |
| Part 5 | `phase8-part5-reconciliation-and-reports.md` | Matching, PDF/CSV reports |
| Part 6 | `phase8-part6-controllers-swagger-testing.md` | Endpoints, OpenAPI, tests |

### Phase 9 — Webhook Service

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase9-part1-project-setup-and-dynamodb.md` | Dependencies, DynamoDB table design |
| Part 2 | `phase9-part2-event-types-and-schema.md` | Event definitions, payload formats |
| Part 3 | `phase9-part3-hmac-signature-and-delivery.md` | HMAC-SHA256, HTTP POST to merchant |
| Part 4 | `phase9-part4-retry-and-dead-letter-queue.md` | Exponential backoff, DLQ |
| Part 5 | `phase9-part5-sqs-listener-and-integration.md` | Consume events from payment-service |
| Part 6 | `phase9-part6-controllers-swagger-testing.md` | Delivery logs API, debugging, tests |

### Phase 10 — Notification Service & AI Fraud Detection

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase10-part1-notification-service-setup.md` | Dependencies, SQS listener |
| Part 2 | `phase10-part2-sns-email-sms-integration.md` | AWS SNS for email/SMS |
| Part 3 | `phase10-part3-fraud-detection-rule-engine.md` | Velocity, amount, geo, device rules |
| Part 4 | `phase10-part4-ai-risk-scoring-ml-model.md` | Decision tree training, inference |
| Part 5 | `phase10-part5-anomaly-detection.md` | Z-score, spending pattern analysis |
| Part 6 | `phase10-part6-integration-and-testing.md` | Connect to payment-service, tests |

### Phase 11 — React Frontend

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase11-part1-merchant-dashboard-setup.md` | Vite + React + TS + Tailwind setup |
| Part 2 | `phase11-part2-authentication-pages.md` | Login, register, forgot password |
| Part 3 | `phase11-part3-dashboard-layout-and-overview.md` | Sidebar, stats cards, charts |
| Part 4 | `phase11-part4-transactions-and-settlements.md` | Transaction list/detail, settlements |
| Part 5 | `phase11-part5-api-keys-and-settings.md` | API keys page, webhook config |
| Part 6 | `phase11-part6-hosted-checkout-setup.md` | Separate React app for checkout |
| Part 7 | `phase11-part7-checkout-payment-forms.md` | Card/UPI/NetBanking forms |
| Part 8 | `phase11-part8-checkout-success-failure.md` | Result pages, merchant redirect |
| Part 9 | `phase11-part9-developer-portal.md` | Stripe-like API docs site |

### Phase 12 — Testing

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase12-part1-testing-strategy-and-setup.md` | Test pyramid, frameworks |
| Part 2 | `phase12-part2-unit-tests-service-layer.md` | JUnit 5 + Mockito |
| Part 3 | `phase12-part3-integration-tests.md` | TestContainers, full flows |
| Part 4 | `phase12-part4-api-tests-and-coverage.md` | REST Assured, JaCoCo |
| Part 5 | `phase12-part5-postman-collection-setup.md` | Postman environments, test scripts |

### Phase 13 — Docker Containerization

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase13-part1-dockerfiles-java-services.md` | Multi-stage builds |
| Part 2 | `phase13-part2-dockerfiles-react-frontends.md` | Node build + Nginx |
| Part 3 | `phase13-part3-docker-compose-full-stack.md` | All services + infra |
| Part 4 | `phase13-part4-networking-and-optimization.md` | Networks, health checks |

### Phase 14 — CI/CD Pipeline

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase14-part1-github-actions-basics.md` | Workflows, jobs, steps |
| Part 2 | `phase14-part2-backend-pipeline.md` | Build, test, Docker push to ECR |
| Part 3 | `phase14-part3-frontend-pipeline.md` | Build, test, deploy to S3 |
| Part 4 | `phase14-part4-deployment-automation.md` | EC2 deploy, secrets |

### Phase 15 — AWS Deployment

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase15-part1-aws-account-and-free-tier.md` | Account setup, credits |
| Part 2 | `phase15-part2-vpc-subnets-security-groups.md` | Networking |
| Part 3 | `phase15-part3-rds-elasticache-dynamodb.md` | Database services |
| Part 4 | `phase15-part4-ec2-docker-deployment.md` | EC2, Docker on EC2 |
| Part 5 | `phase15-part5-alb-and-routing.md` | Load balancer |
| Part 6 | `phase15-part6-s3-cloudfront-frontend.md` | Frontend hosting |
| Part 7 | `phase15-part7-end-to-end-verification.md` | Full system test |
| Part 8 | `phase15-part8-teardown-guide.md` | Cleanup, save credits |

### Phase 16 — Monitoring & Observability

| Part | Document | Content |
|------|----------|---------|
| Part 1 | `phase16-part1-spring-boot-actuator.md` | Health, metrics, info |
| Part 2 | `phase16-part2-structured-logging.md` | JSON logs, correlation IDs |
| Part 3 | `phase16-part3-cloudwatch-dashboards-alarms.md` | Dashboards, alerting |

---

## How to Follow This Guide

1. **Phase 1** — Read all 6 parts (domain knowledge, ISO 8583, setup). No coding yet.
2. **Phase 2** — Read all 6 parts (design, schema, APIs). Understand before coding.
3. **Phase 3 onwards** — Code step by step. Each part has line-by-line instructions.
4. **After each phase** — Run locally with Docker Compose and verify.
5. **Phase 15** — Deploy to AWS using free tier credits.

Each document part contains:
- **Concepts** — What we're building and why
- **Step-by-step code** — Every file, every line explained
- **Verification** — How to test it works
- **Interview notes** — What to say about this in interviews

---

## Quick Start (After Project Complete)

```bash
# Clone
git clone https://github.com/your-username/payflow-payment-gateway.git
cd payflow-payment-gateway

# Start infrastructure (DB, Redis, DynamoDB)
docker-compose -f docker-compose-infra.yml up -d

# Start all services
docker-compose up -d

# Access Points
# ┌──────────────────────────────────────────────────────────┐
# │ Eureka Dashboard:        http://localhost:8761            │
# │ API Gateway:             http://localhost:8080            │
# │ Swagger (Aggregated):    http://localhost:8080/swagger-ui │
# │ Identity Swagger:        http://localhost:8081/swagger-ui │
# │ Payment Swagger:         http://localhost:8083/swagger-ui │
# │ Merchant Dashboard:      http://localhost:3000            │
# │ Hosted Checkout:         http://localhost:3001            │
# │ Developer Portal:        http://localhost:3002            │
# └──────────────────────────────────────────────────────────┘
```

---

## License

Private — Interview preparation project by Tejaswi
