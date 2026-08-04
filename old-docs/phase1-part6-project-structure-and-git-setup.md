# Phase 1 — Part 6: Project Structure & Git Setup

> This document shows the complete folder structure and how to initialize
> the Git repository. After this, Phase 1 is complete and we move to design.

---

## 1. Create Project Root Folder

Open Command Prompt:
```cmd
mkdir payflow-payment-gateway
cd payflow-payment-gateway
```

---

## 2. Complete Project Structure

Here's every folder and key file we'll create across all phases:

```
payflow-payment-gateway/
│
├── pom.xml                              ← Parent POM (multi-module)
├── README.md                            ← Project overview
├── .gitignore                           ← Files to ignore in Git
├── docker-compose.yml                   ← All services for local dev
├── docker-compose-infra.yml             ← Only infrastructure (DB, Redis)
│
├── docs/                                ← Documentation (Phase 1-16)
│   ├── phase1-project-overview.md
│   ├── phase1-part1-payment-domain-knowledge.md
│   ├── phase1-part2-iso8583-protocol-deep-dive.md
│   ├── phase1-part3-architecture-and-design-decisions.md
│   ├── phase1-part4-aws-free-tier-plan.md
│   ├── phase1-part5-development-environment-setup.md
│   ├── phase1-part6-project-structure-and-git-setup.md
│   ├── requirements-document.md
│   ├── design-document.md
│   └── ... (Phase 2-16 docs created as we progress)
│
├── docs/postman/                        ← Postman collections
│   ├── PayFlow-Local.postman_environment.json
│   ├── PayFlow-AWS.postman_environment.json
│   └── PayFlow-API.postman_collection.json
│
├── common-lib/                          ← Shared Java library
│   ├── pom.xml
│   └── src/main/java/com/payflow/common/
│       ├── dto/
│       │   ├── ApiResponse.java         ← Standard response wrapper
│       │   ├── ErrorResponse.java       ← Standard error format
│       │   └── PagedResponse.java       ← Pagination wrapper
│       ├── exception/
│       │   ├── PayflowException.java    ← Base exception
│       │   ├── ResourceNotFoundException.java
│       │   ├── DuplicateResourceException.java
│       │   └── PaymentDeclinedException.java
│       ├── util/
│       │   ├── IdGenerator.java         ← Generate pay_xxx, ord_xxx IDs
│       │   └── DateUtil.java
│       └── constant/
│           ├── PaymentStatus.java       ← Enum: CREATED, AUTHORIZED, etc.
│           ├── PaymentMethod.java       ← Enum: CARD, UPI, NETBANKING
│           └── Currency.java            ← Enum: INR, USD
│
├── service-registry/                    ← Eureka Server
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/payflow/registry/
│       │   └── ServiceRegistryApplication.java
│       └── resources/
│           └── application.yml
│
├── config-server/                       ← Spring Cloud Config
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/main/
│   │   ├── java/com/payflow/config/
│   │   │   └── ConfigServerApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── configurations/                  ← Per-service configs
│       ├── identity-service.yml
│       ├── merchant-service.yml
│       ├── payment-service.yml
│       ├── routing-service.yml
│       ├── settlement-service.yml
│       ├── webhook-service.yml
│       └── notification-service.yml
│
├── api-gateway/                         ← Spring Cloud Gateway
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/payflow/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   ├── config/
│       │   │   ├── GatewayConfig.java
│       │   │   └── SwaggerConfig.java   ← Aggregate all service docs
│       │   └── filter/
│       │       ├── AuthenticationFilter.java
│       │       └── RateLimitFilter.java
│       └── resources/
│           └── application.yml
│
├── identity-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/payflow/identity/
│       │   │   ├── IdentityServiceApplication.java
│       │   │   ├── controller/
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   ├── model/
│       │   │   ├── dto/
│       │   │   ├── config/
│       │   │   ├── security/
│       │   │   └── exception/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_users_table.sql
│       └── test/java/com/payflow/identity/
│
├── merchant-service/                    ← Same structure as identity
├── payment-service/                     ← + statemachine/ + client/
├── routing-service/                     ← + iso8583/ package
├── settlement-service/                  ← + batch/ + scheduler/
├── webhook-service/                     ← + listener/ (SQS)
├── notification-service/                ← + listener/ (SQS)
│
├── bank-simulator/                      ← ISO 8583 TCP Server
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/payflow/simulator/
│       ├── BankSimulatorApplication.java
│       ├── server/TcpServer.java
│       ├── handler/Iso8583RequestHandler.java
│       ├── handler/ResponseGenerator.java
│       └── rules/SimulatorRules.java
│
├── frontend-dashboard/                  ← React: Merchant Dashboard
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── Dockerfile
│   ├── index.html
│   └── src/
│       ├── App.tsx
│       ├── main.tsx
│       ├── components/
│       ├── pages/
│       ├── services/ (API calls)
│       ├── hooks/
│       ├── types/
│       └── utils/
│
├── frontend-checkout/                   ← React: Hosted Payment Page
│   ├── (same structure as dashboard)
│   └── src/
│       ├── pages/
│       │   ├── PaymentPage.tsx
│       │   ├── CardForm.tsx
│       │   ├── UpiForm.tsx
│       │   ├── OtpPage.tsx
│       │   ├── SuccessPage.tsx
│       │   └── FailurePage.tsx
│       └── ...
│
├── frontend-developer-portal/           ← React: API Docs (like Stripe docs)
│   ├── (same structure)
│   └── src/
│       ├── pages/
│       │   ├── GettingStarted.tsx
│       │   ├── ApiReference.tsx
│       │   ├── WebhooksGuide.tsx
│       │   └── CodeExamples.tsx
│       └── ...
│
└── .github/
    └── workflows/
        ├── ci-backend.yml               ← Java: build + test + Docker
        └── ci-frontend.yml              ← React: build + test + deploy
```

---

## 3. Initialize Git Repository

```cmd
cd payflow-payment-gateway

git init
git add .
git commit -m "Phase 1: Project documentation - domain knowledge, ISO 8583, architecture, setup"
```

---

## 4. Create GitHub Repository

**Step 1:** Go to https://github.com/new

**Step 2:** Fill in:
- Repository name: `payflow-payment-gateway`
- Description: "Production-ready Payment Gateway with ISO 8583 - Java Spring Boot Microservices"
- Visibility: **Public** (needed for free GitHub Actions CI/CD)
- Do NOT initialize with README (we already have one)

**Step 3:** Link local to remote:
```cmd
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/payflow-payment-gateway.git
git push -u origin main
```

---

## 5. Branch Strategy

```
main          ← Production-ready code (protected branch)
  │
  ├── develop ← Integration branch (merge features here)
  │     │
  │     ├── feature/phase3-eureka-setup
  │     ├── feature/phase4-identity-service
  │     ├── feature/phase6-payment-service
  │     └── ...
  │
  └── release/v1.0 ← When ready for AWS deployment
```

Create develop branch:
```cmd
git checkout -b develop
git push -u origin develop
```

---

## 6. Phase 1 Complete — Summary

After completing Phase 1 (all 6 parts), you now have:

| What You Know | Document |
|---------------|----------|
| How payments work end-to-end | Part 1 |
| ISO 8583 protocol in detail | Part 2 |
| Why we chose each technology | Part 3 |
| AWS costs and strategy | Part 4 |
| All tools installed and verified | Part 5 |
| Project structure and Git ready | Part 6 |

**You have NOT written any code yet.** That's intentional.
Phase 2 designs everything (DB schema, APIs, messages).
Phase 3 starts actual coding.

---

## Next Step

→ Move to **Phase 2: System Design**
→ Start with **`phase2-part1-high-level-design.md`**

In Phase 2, we will:
1. Draw complete architecture with all connections
2. Design every database table (SQL CREATE statements)
3. Define every REST API endpoint with request/response examples
4. Define ISO 8583 message specifications
5. Define all SQS message formats and webhook event schemas
