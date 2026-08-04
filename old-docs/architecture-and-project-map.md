# PayFlow — Complete Architecture, Project Map & Technology Reference

**Version:** 1.0
**Date:** July 2026
**Author:** Tejaswi
**Status:** Complete

---

## 1. System Architecture (Complete Diagram)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                      INTERNET                                                 │
│                                                                                               │
│   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐   ┌────────────────┐  │
│   │ Customer Browser │   │ Merchant Server  │   │ Merchant Browser │   │   Developer    │  │
│   │ (Hosted Checkout)│   │ (API Integration)│   │ (Dashboard)      │   │   (Dev Portal) │  │
│   │                  │   │                  │   │                  │   │                │  │
│   │ React App        │   │ Uses sk_pay_xxx │   │ React App        │   │ React App      │  │
│   │ Port: 3001       │   │ in X-Api-Key     │   │ Port: 3000       │   │ Port: 3002     │  │
│   └────────┬─────────┘   └────────┬─────────┘   └────────┬─────────┘   └────────────────┘  │
│            │                       │                       │                                  │
│            └───────────────────────┼───────────────────────┘                                  │
│                                    │                                                          │
│                                    │ ALL traffic → single URL (port 8080)                     │
│                                    ▼                                                          │
└────────────────────────────────────┼──────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼──────────────────────────────────────────────────────────┐
│                                                                                                │
│                         ╔══════════════════════════════════════╗                               │
│                         ║     API GATEWAY (Port 8080)          ║                               │
│                         ║     Spring Cloud Gateway             ║                               │
│                         ║                                      ║                               │
│                         ║  ┌────────────┐ ┌────────────────┐  ║                               │
│                         ║  │Rate Limiter│ │Correlation ID  │  ║                               │
│                         ║  │(Redis 100/ │ │Filter (adds    │  ║                               │
│                         ║  │ min/key)   │ │ req_xxxxxxxx)  │  ║                               │
│                         ║  └────────────┘ └────────────────┘  ║                               │
│                         ║                                      ║                               │
│                         ║  ROUTES:                              ║                               │
│                         ║  /v1/auth/**     → IDENTITY-SERVICE  ║                               │
│                         ║  /v1/merchants/**→ MERCHANT-SERVICE  ║                               │
│                         ║  /v1/orders/**   → PAYMENT-SERVICE   ║                               │
│                         ║  /v1/payments/** → PAYMENT-SERVICE   ║                               │
│                         ║  /v1/settlements/**→SETTLEMENT-SVC   ║                               │
│                         ║  /v1/webhooks/** → WEBHOOK-SERVICE   ║                               │
│                         ╚══════════════════════════════════════╝                               │
│                              │     │     │     │     │     │                                   │
└──────────────────────────────┼─────┼─────┼─────┼─────┼─────┼─────────────────────────────────┘
                               │     │     │     │     │     │
                               ▼     ▼     ▼     ▼     ▼     ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                BACKEND MICROSERVICES                                               │
│                                                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                              │ │
│  │  ┌───────────────┐   ┌───────────────┐   ┌────────────────────────────────────────────┐    │ │
│  │  │  IDENTITY     │   │  MERCHANT     │   │           PAYMENT SERVICE                  │    │ │
│  │  │  SERVICE      │   │  SERVICE      │   │           (Port 8083)                      │    │ │
│  │  │  (Port 8081)  │   │  (Port 8082)  │   │                                            │    │ │
│  │  │               │   │               │   │  ┌──────────────────────────────────────┐  │    │ │
│  │  │  Endpoints:   │   │  Endpoints:   │   │  │ Core Payment Flow:                   │  │    │ │
│  │  │  POST /register│   │  POST /create │   │  │                                      │  │    │ │
│  │  │  POST /login  │   │  POST /api-keys│   │  │ 1. Check Idempotency (Redis)        │  │    │ │
│  │  │  POST /refresh│   │  GET  /get     │   │  │ 2. Validate Order                    │  │    │ │
│  │  │               │   │  PUT  /webhook │   │  │ 3. Fraud Check (score 0-100)         │  │    │ │
│  │  │  Tech:        │   │  POST /revoke  │   │  │ 4. Call Routing Service (Feign)      │  │    │ │
│  │  │  • JWT tokens │   │               │   │  │ 5. Save to DB                         │  │    │ │
│  │  │  • BCrypt     │   │  Tech:        │   │  │ 6. Publish SQS event                  │  │    │ │
│  │  │  • Spring Sec │   │  • SHA-256    │   │  │ 7. Return response                    │  │    │ │
│  │  │               │   │    key hashing│   │  │                                      │  │    │ │
│  │  │  DB: PG       │   │  • API key    │   │  │ State Machine:                       │  │    │ │
│  │  │  (identity)   │   │    generation │   │  │ CREATED→PROCESSING→AUTHORIZED        │  │    │ │
│  │  │               │   │               │   │  │ →CAPTURED→SETTLED                     │  │    │ │
│  │  │  Swagger:     │   │  DB: PG       │   │  │ →VOIDED / →REFUNDED / →FAILED        │  │    │ │
│  │  │  :8081/swagger│   │  (merchant)   │   │  └──────────────────────────────────────┘  │    │ │
│  │  └───────────────┘   │               │   │                                            │    │ │
│  │                       │  Swagger:     │   │  DB: PG (payment) + Redis                 │    │ │
│  │                       │  :8082/swagger│   │  Swagger: :8083/swagger-ui.html            │    │ │
│  │                       └───────────────┘   └─────────────────────┬──────────────────────┘    │ │
│  │                                                                  │                           │ │
│  │                                                                  │ Feign HTTP (sync)         │ │
│  │                                                                  ▼                           │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │                    ROUTING SERVICE (Port 8084)                                         │  │ │
│  │  │                                                                                        │  │ │
│  │  │   ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────────────────────┐   │  │ │
│  │  │   │Smart Routing │  │ ISO 8583 Codec   │  │      TCP Client (BankTcpClient)      │   │  │ │
│  │  │   │Engine        │  │                  │  │                                      │   │  │ │
│  │  │   │              │  │ • Iso8583Message │  │  • Opens TCP socket to bank:9000     │   │  │ │
│  │  │   │ • Score each │  │ • Iso8583Encoder │  │  • Sends [2-byte len][ISO bytes]     │   │  │ │
│  │  │   │   route      │  │   (Java→binary)  │  │  • Receives [2-byte len][response]  │   │  │ │
│  │  │   │ • Pick best  │  │ • Iso8583Decoder │  │  • Timeout: 5 seconds               │   │  │ │
│  │  │   │ • Failover   │  │   (binary→Java)  │  │  • If timeout → send reversal       │   │  │ │
│  │  │   │   if fails   │  │ • FieldDefs     │  │                                      │   │  │ │
│  │  │   └──────────────┘  └──────────────────┘  └─────────────────┬────────────────────┘   │  │ │
│  │  │                                                               │                        │  │ │
│  │  │   Cache: Redis (route success rates, latency metrics)         │ TCP (ISO 8583 binary)  │  │ │
│  │  │   Swagger: :8084/swagger-ui.html                              │                        │  │ │
│  │  └──────────────────────────────────────────────────────────────┼────────────────────────┘  │ │
│  │                                                                  │                           │ │
│  │                                                                  ▼                           │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │                    BANK SIMULATOR (Port 9000 — TCP Server)                             │  │ │
│  │  │                                                                                        │  │ │
│  │  │   Simulates Visa/Mastercard/UPI network:                                               │  │ │
│  │  │   • Receives ISO 8583 auth request (MTI 0100)                                         │  │ │
│  │  │   • Applies rules based on card number:                                                │  │ │
│  │  │     ├── 4111 1111 1111 1111 → APPROVE (code 00, auth code generated)                  │  │ │
│  │  │     ├── 4000 0000 0000 0002 → DECLINE (code 51, insufficient funds)                   │  │ │
│  │  │     ├── 4000 0000 0000 0077 → TIMEOUT (no response — tests failover)                  │  │ │
│  │  │     └── 5500 / 6521 test cards → APPROVE                                              │  │ │
│  │  │   • Returns ISO 8583 response (MTI 0110)                                              │  │ │
│  │  │   • Simulates 100-300ms bank processing latency                                       │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                              │ │
│  │  ┌───────────────┐   ┌───────────────┐   ┌───────────────┐                                 │ │
│  │  │ SETTLEMENT    │   │  WEBHOOK      │   │ NOTIFICATION  │                                 │ │
│  │  │ SERVICE       │   │  SERVICE      │   │ SERVICE       │                                 │ │
│  │  │ (Port 8085)   │   │  (Port 8086)  │   │ (Port 8087)   │                                 │ │
│  │  │               │   │               │   │               │                                 │ │
│  │  │ • Daily batch │   │ • Reads SQS   │   │ • Reads SQS   │                                 │ │
│  │  │   (midnight)  │   │ • Signs HMAC  │   │ • Sends email │                                 │ │
│  │  │ • Fee calc    │   │ • POST to     │   │   (AWS SNS)   │                                 │ │
│  │  │   (MDR + GST) │   │   merchant URL│   │ • Sends SMS   │                                 │ │
│  │  │ • Payout      │   │ • Retries 5x  │   │   (AWS SNS)   │                                 │ │
│  │  │ • Spring Batch│   │ • DLQ handler │   │               │                                 │ │
│  │  │               │   │               │   │               │                                 │ │
│  │  │ DB: PG        │   │ DB: DynamoDB  │   │ Queue: SQS    │                                 │ │
│  │  │ (settlement)  │   │ Queue: SQS    │   │ Notify: SNS   │                                 │ │
│  │  └───────────────┘   └───────────────┘   └───────────────┘                                 │ │
│  │                                                                                              │ │
│  └──────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            INFRASTRUCTURE SERVICES                                                 │
│                                                                                                    │
│  ┌────────────────────────────────────┐    ┌────────────────────────────────────┐                 │
│  │     SERVICE REGISTRY (Eureka)       │    │       CONFIG SERVER                 │                 │
│  │     Port: 8761                      │    │       Port: 8888                    │                 │
│  │                                     │    │                                     │                 │
│  │  • ALL 11 services register here   │    │  • Stores YAML config for each     │                 │
│  │  • Heartbeat every 30 seconds      │    │    service in one place             │                 │
│  │  • Dashboard: http://localhost:8761 │    │  • Services fetch on startup       │                 │
│  │  • If service dies → removed       │    │  • Change once → all services get  │                 │
│  │  • Enables lb:// routing in gateway│    │  • configurations/ folder           │                 │
│  │                                     │    │    ├── identity-service.yml         │                 │
│  │  Tech: Spring Cloud Netflix Eureka  │    │    ├── payment-service.yml          │                 │
│  └────────────────────────────────────┘    │    └── merchant-service.yml          │                 │
│                                             │                                     │                 │
│                                             │  Tech: Spring Cloud Config Server   │                 │
│                                             └────────────────────────────────────┘                 │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA & MESSAGING LAYER                                                │
│                                                                                                    │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌────────────┐ ┌────────────┐  │
│  │   PostgreSQL     │ │      Redis       │ │    DynamoDB      │ │    SQS     │ │    SNS     │  │
│  │   (Port 5432)    │ │   (Port 6379)    │ │   (Port 8000)    │ │            │ │            │  │
│  │                  │ │                  │ │                  │ │            │ │            │  │
│  │ 4 schemas:       │ │ Stores:          │ │ 3 tables:        │ │ 4 queues:  │ │ 2 topics:  │  │
│  │ ├── identity    │ │ ├── idempotency  │ │ ├── webhook_     │ │ ├── payment│ │ ├── email  │  │
│  │ │   (users)     │ │ │   keys (24h)   │ │ │   events       │ │ │   _events│ │ └── sms   │  │
│  │ ├── merchant    │ │ ├── rate limit   │ │ ├── routing_     │ │ ├── webhook│ │            │  │
│  │ │   (merchants, │ │ │   counters     │ │ │   metrics      │ │ │   _dlvry │ │ Always     │  │
│  │ │    api_keys)  │ │ ├── route cache  │ │ └── audit_trail  │ │ ├── notify │ │ Free!      │  │
│  │ ├── payment     │ │ └── JWT blacklist│ │                  │ │ └── DLQs   │ │            │  │
│  │ │   (orders,    │ │                  │ │ Always Free!     │ │            │ │            │  │
│  │ │    payments,  │ │ AWS: ElastiCache │ │ (25GB + 25 WCU)  │ │ Always     │ │            │  │
│  │ │    refunds)   │ │ Local: Docker    │ │                  │ │ Free!      │ │            │  │
│  │ └── settlement  │ │                  │ │                  │ │ (1M/month) │ │            │  │
│  │                  │ │                  │ │                  │ │            │ │            │  │
│  │ AWS: RDS         │ │ Cost: ~$12/mo   │ │ Cost: $0         │ │ Cost: $0   │ │ Cost: $0   │  │
│  │ Cost: ~$15/mo    │ │ (from credits)  │ │                  │ │            │ │            │  │
│  │ (from credits)   │ │                  │ │                  │ │            │ │            │  │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘ └────────────┘ └────────────┘  │
│                                                                                                    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete Project Structure (Every Folder & File)

```
payflow-payment-gateway/                     ← ROOT (Multi-module Maven Project)
│
├── pom.xml                                  ← Parent POM (manages versions for all modules)
├── README.md                                ← Project overview (tech stack, microservices, phases)
├── CONTRIBUTING.md                          ← How to contribute (setup, branches, commit format)
├── .gitignore                               ← Git ignore rules (target/, node_modules/, .env)
│
├── docker-compose.yml                       ← Full stack: all services + infrastructure
├── docker-compose-infra.yml                 ← Infrastructure only: PostgreSQL, Redis, DynamoDB, LocalStack
├── docker-compose.prod.yml                  ← Production: Java services only (AWS manages infra)
│
├── docker/                                  ← Docker initialization scripts
│   ├── init-db.sql                          ← Creates 4 PostgreSQL schemas on first boot
│   └── init-localstack.sh                   ← Creates SQS queues + SNS topics in LocalStack
│
├── .github/workflows/                       ← CI/CD Pipelines (GitHub Actions)
│   ├── ci-backend.yml                       ← Java: build → test → Docker → push → security scan
│   └── ci-frontend.yml                      ← React: lint → build → deploy to S3 → CloudFront invalidation
│
├── docs/                                    ← 98 Documentation Files (Phase 1-16)
│   ├── requirements-document.md             ← PRD (90+ requirements)
│   ├── design-document.md                   ← Architecture + ER + Flows + AI features
│   ├── architecture-and-project-map.md      ← THIS FILE
│   ├── phase1-*.md through phase16-*.md     ← 95 step-by-step tutorial documents
│   └── postman/                             ← API testing collections
│       ├── PayFlow-API.postman_collection.json
│       ├── PayFlow-Local.postman_environment.json
│       └── PayFlow-AWS.postman_environment.json
│
├── common-lib/                              ← SHARED JAVA LIBRARY (used by all services)
│   ├── pom.xml
│   └── src/main/java/com/payflow/common/
│       ├── dto/ApiResponse.java             ← Standard {success, data, error, timestamp} wrapper
│       ├── dto/ErrorDetail.java             ← Error structure {code, message, details}
│       ├── dto/PagedResponse.java           ← Paginated list response
│       ├── constant/PaymentStatus.java      ← Enum: CREATED, AUTHORIZED, CAPTURED...
│       ├── constant/PaymentMethod.java      ← Enum: CARD, UPI, NETBANKING, WALLET
│       ├── exception/PayflowException.java  ← Base exception (code + HTTP status)
│       ├── exception/ResourceNotFoundException.java  ← 404
│       ├── exception/DuplicateResourceException.java ← 409
│       ├── exception/InvalidStateTransitionException.java ← 400
│       ├── exception/GlobalExceptionHandler.java ← @RestControllerAdvice (catches all errors)
│       └── util/IdGenerator.java            ← Generates: pay_xxx, ord_xxx, merch_xxx
│
├── service-registry/                        ← EUREKA SERVER (Service Discovery)
│   ├── pom.xml                              │ Tech: Spring Cloud Netflix Eureka
│   ├── Dockerfile                           │ Port: 8761
│   └── src/...ServiceRegistryApplication.java │ Dashboard: http://localhost:8761
│
├── config-server/                           ← SPRING CLOUD CONFIG (Centralized Config)
│   ├── pom.xml                              │ Tech: Spring Cloud Config Server
│   ├── Dockerfile                           │ Port: 8888
│   ├── src/...ConfigServerApplication.java  │
│   └── src/.../configurations/              │ Per-service YAML files:
│       ├── identity-service.yml             │   DB URL, JWT secret, Eureka URL
│       ├── payment-service.yml              │   DB URL, Redis, circuit breaker
│       └── merchant-service.yml             │   DB URL, Eureka URL
│
├── api-gateway/                             ← API GATEWAY (Single Entry Point)
│   ├── pom.xml                              │ Tech: Spring Cloud Gateway (WebFlux)
│   ├── Dockerfile                           │ Port: 8080
│   └── src/...                              │
│       ├── ApiGatewayApplication.java       │
│       ├── filter/RateLimitFilter.java      │ 100 req/min per API key (Redis)
│       └── filter/CorrelationIdFilter.java  │ Adds X-Correlation-Id to every request
│
├── identity-service/                        ← USER AUTH (Registration, Login, JWT)
│   ├── pom.xml                              │ Tech: Spring Security + JWT (jjwt)
│   ├── Dockerfile                           │ Port: 8081
│   └── src/...                              │ DB: PostgreSQL (identity schema)
│       ├── model/User.java                  │ Swagger: /swagger-ui.html
│       ├── dto/RegisterRequest.java         │
│       ├── dto/LoginRequest.java            │ Endpoints:
│       ├── dto/AuthResponse.java            │ POST /v1/auth/register
│       ├── repository/UserRepository.java   │ POST /v1/auth/login
│       ├── service/JwtService.java          │
│       ├── service/AuthService.java         │ Security: BCrypt (strength 12)
│       ├── controller/AuthController.java   │ Tokens: Access (15min) + Refresh (7d)
│       └── config/SecurityConfig.java       │
│
├── merchant-service/                        ← MERCHANT MANAGEMENT
│   ├── pom.xml                              │ Tech: Spring Data JPA + SHA-256
│   ├── Dockerfile                           │ Port: 8082
│   └── src/...                              │ DB: PostgreSQL (merchant schema)
│       ├── model/Merchant.java              │
│       ├── model/ApiKey.java                │ Endpoints:
│       ├── repository/MerchantRepository.java│ POST /v1/merchants (create)
│       ├── repository/ApiKeyRepository.java │ POST /v1/merchants/{id}/api-keys (generate keys)
│       ├── service/MerchantService.java     │ PUT  /v1/merchants/{id}/webhook
│       └── controller/MerchantController.java│ POST /v1/merchants/{id}/api-keys/{id}/revoke
│
├── payment-service/                         ← CORE PAYMENT PROCESSING
│   ├── pom.xml                              │ Tech: JPA + Redis + Feign + Resilience4j
│   ├── Dockerfile                           │ Port: 8083
│   └── src/...                              │ DB: PostgreSQL (payment) + Redis
│       ├── model/Payment.java               │
│       ├── model/Order.java                 │ Endpoints:
│       ├── model/Refund.java                │ POST /v1/orders (create order)
│       ├── dto/PaymentRequest.java          │ POST /v1/payments (authorize)
│       ├── dto/PaymentResponse.java         │ POST /v1/payments/{id}/capture
│       ├── dto/OrderRequest.java            │ POST /v1/payments/{id}/void
│       ├── dto/RefundRequest.java           │ POST /v1/payments/{id}/refund
│       ├── statemachine/PaymentStateMachine.java │ GET  /v1/payments/{id}
│       ├── service/PaymentProcessorService.java  │
│       ├── service/OrderService.java        │ Features: State machine, Idempotency,
│       ├── service/IdempotencyService.java  │ Fraud scoring, Feign to routing-service
│       ├── controller/PaymentController.java│
│       └── controller/OrderController.java  │
│   └── src/test/.../PaymentProcessorServiceTest.java ← Unit test (JUnit 5 + Mockito)
│
├── routing-service/                         ← SMART ROUTING + ISO 8583
│   ├── pom.xml                              │ Tech: Netty TCP + Custom ISO 8583
│   ├── Dockerfile                           │ Port: 8084
│   └── src/...                              │ Cache: Redis (route metrics)
│       ├── iso8583/Iso8583Message.java      │
│       ├── iso8583/Iso8583Encoder.java      │ Endpoints:
│       ├── iso8583/Iso8583Decoder.java      │ POST /internal/route (called by payment-service)
│       ├── iso8583/FieldDefinitions.java    │
│       ├── iso8583/FieldDefinition.java     │ Features: Multi-armed bandit routing,
│       ├── iso8583/FieldType.java           │ ISO 8583 encode/decode, TCP socket,
│       ├── service/RoutingEngine.java       │ Failover, Reversal on timeout
│       ├── service/BankTcpClient.java       │
│       ├── dto/RouteRequest.java            │
│       ├── dto/RouteResponse.java           │
│       └── controller/RoutingController.java│
│
├── settlement-service/                      ← BATCH SETTLEMENT
│   ├── pom.xml                              │ Tech: Spring Batch + @Scheduled
│   ├── Dockerfile                           │ Port: 8085
│   └── src/...                              │ DB: PostgreSQL (settlement schema)
│       ├── model/Settlement.java            │
│       ├── repository/SettlementRepository.java │ Endpoints:
│       ├── service/FeeCalculator.java       │ GET  /v1/settlements (list)
│       ├── service/SettlementService.java   │ POST /internal/trigger (admin)
│       └── controller/SettlementController.java │
│                                            │ Features: Daily midnight cron,
│                                            │ MDR + GST fee calculation (BigDecimal),
│                                            │ Spring Batch chunked processing, PayoutService
│
├── webhook-service/                         ← RELIABLE WEBHOOK DELIVERY
│   ├── pom.xml                              │ Tech: HMAC-SHA256 + SQS + DynamoDB
│   ├── Dockerfile                           │ Port: 8086
│   └── src/...                              │ DB: DynamoDB (webhook_events)
│       ├── service/SignatureGenerator.java   │
│       ├── service/WebhookDispatcher.java   │ Endpoints:
│       ├── controller/WebhookController.java│ GET  /v1/webhooks/events
│       └── dto/WebhookEvent.java            │ POST /v1/webhooks/events/{id}/retry
│                                            │
│                                            │ Features: HMAC-SHA256 signing,
│                                            │ Exponential backoff retry (5 attempts),
│                                            │ Dead Letter Queue, Delivery logs
│
├── notification-service/                    ← EMAIL/SMS VIA AWS SNS
│   ├── pom.xml                              │ Tech: AWS SNS + SQS consumer
│   ├── Dockerfile                           │ Port: 8087
│   └── src/...                              │
│       ├── service/NotificationService.java │ Endpoints:
│       ├── controller/NotificationController.java │ POST /internal/notify
│       └── dto/NotificationRequest.java     │
│                                            │ Features: Email templates,
│                                            │ SMS via SNS, SQS queue consumer
│
├── bank-simulator/                          ← ISO 8583 TCP BANK MOCK
│   ├── pom.xml                              │ Tech: Plain Java TCP ServerSocket
│   ├── Dockerfile                           │ Port: 9000 (TCP, not HTTP)
│   └── src/...                              │
│       ├── BankSimulatorApplication.java    │ NOT a REST service — pure TCP server
│       ├── server/TcpServer.java            │ Simulates Visa/Mastercard behavior
│       └── handler/Iso8583RequestHandler.java │ Configurable approve/decline rules
│
├── frontend-dashboard/                      ← REACT: MERCHANT DASHBOARD
│   ├── package.json                         │ Tech: React 18 + TypeScript + Vite + Tailwind
│   ├── vite.config.ts                       │ Port: 3000
│   ├── tsconfig.json                        │
│   ├── tailwind.config.js                   │ Pages:
│   ├── index.html                           │ • Login / Register
│   └── src/                                 │ • Dashboard (stats + charts)
│       ├── main.tsx                         │ • Transactions (list + search)
│       ├── App.tsx                           │ • Settlements
│       ├── pages/LoginPage.tsx              │ • API Keys management
│       ├── pages/DashboardPage.tsx          │ • Settings (webhooks, fees)
│       ├── pages/TransactionsPage.tsx       │
│       └── services/api.ts                  │ API: Axios + Bearer token
│
├── frontend-checkout/                       ← REACT: HOSTED PAYMENT PAGE
│   ├── package.json                         │ Tech: React 18 + TypeScript + Vite + Tailwind
│   ├── vite.config.ts                       │ Port: 3001
│   ├── tsconfig.json                        │
│   └── src/                                 │ Pages:
│       ├── main.tsx                         │ • Payment form (card number, expiry, CVV)
│       ├── App.tsx                           │ • UPI payment (enter VPA)
│       ├── pages/PaymentPage.tsx            │ • 3D Secure OTP
│       ├── pages/SuccessPage.tsx            │ • Success page (green checkmark)
│       └── pages/FailurePage.tsx            │ • Failure page (retry button)
│
└── frontend-developer-portal/               ← REACT: API DOCS SITE (Like Stripe Docs)
    ├── package.json                         │ Tech: React 18 + TypeScript + Vite + Tailwind
    ├── vite.config.ts                       │ Port: 3002
    ├── tsconfig.json                        │
    └── src/                                 │ Pages:
        ├── main.tsx                         │ • Home (welcome + quick links)
        ├── App.tsx                           │ • Getting Started (3-step guide)
        ├── components/Sidebar.tsx           │ • API Reference (all endpoints)
        ├── pages/HomePage.tsx               │ • Authentication (API keys)
        ├── pages/GettingStartedPage.tsx     │ • Webhooks (events, HMAC, retry)
        ├── pages/ApiReferencePage.tsx       │
        ├── pages/AuthenticationPage.tsx     │ Design: Dark sidebar, white content,
        └── pages/WebhooksPage.tsx           │ Code examples (curl), Tables
```

---

## 3. Tech Stack (Complete)

### Backend

| Technology | Version | Used In | Purpose |
|-----------|---------|---------|---------|
| Java | 17 (LTS) | All services | Core language |
| Spring Boot | 3.2.5 | All services | Application framework |
| Spring Cloud Gateway | 2023.0.1 | api-gateway | Reactive routing, filters |
| Spring Cloud Eureka | 2023.0.1 | service-registry + all clients | Service discovery |
| Spring Cloud Config | 2023.0.1 | config-server + all clients | Centralized config |
| Spring Cloud OpenFeign | 2023.0.1 | payment → routing | Declarative HTTP client |
| Spring Security | 6.x | identity-service | JWT auth, BCrypt, roles |
| Spring Data JPA | 3.2.x | identity, merchant, payment, settlement | PostgreSQL ORM |
| Spring Data Redis | 3.2.x | payment, gateway | Cache, rate limiting |
| Spring Batch | 5.x | settlement-service | Chunked batch processing |
| Resilience4j | 2.x | payment-service | Circuit breaker, retry |
| SpringDoc OpenAPI | 2.3.x | All business services | Swagger UI |
| Flyway | 9.x | identity, merchant, payment, settlement | DB migrations |
| Netty | 4.1.x | routing → bank | TCP socket (ISO 8583) |
| jjwt | 0.12.5 | identity-service | JWT token library |
| MapStruct | 1.5.5 | (available) | DTO mapping |
| Lombok | 1.18.32 | All services | Boilerplate reduction |
| Jackson | 2.16.x | All services | JSON serialization |

### Frontend

| Technology | Version | Used In | Purpose |
|-----------|---------|---------|---------|
| React | 18.2 | All 3 frontends | UI framework |
| TypeScript | 5.x | All 3 frontends | Type safety |
| Vite | 5.x | All 3 frontends | Fast build tool |
| Tailwind CSS | 3.x | All 3 frontends | Utility-first styling |
| React Router | 6.x | All 3 frontends | Client-side routing |
| TanStack Query | 5.x | dashboard | API state + caching |
| Axios | 1.6 | dashboard, checkout | HTTP client |
| Recharts | 2.x | dashboard | Analytics charts |
| React Hook Form | 7.x | dashboard | Form handling |

### Database & Messaging

| Technology | Used For | Cost |
|-----------|----------|------|
| PostgreSQL 15 | Users, merchants, payments, settlements (ACID) | ~$15/mo (RDS) |
| Redis 7 | Idempotency, rate limiting, routing cache | ~$12/mo (ElastiCache) |
| DynamoDB | Webhook events, routing metrics, audit trail | $0 (always free) |
| Amazon SQS | Async event queues (payment, webhook, notification) | $0 (always free) |
| Amazon SNS | Email/SMS notifications | $0 (always free) |

### Infrastructure & DevOps

| Technology | Purpose |
|-----------|---------|
| Docker | Container per service (11 Dockerfiles) |
| Docker Compose × 3 | Local dev, full stack, production |
| GitHub Actions × 2 | Backend CI + Frontend CI |
| AWS EC2 (t3.micro) | Run Docker containers (~$8.50/mo) |
| AWS ALB | Load balancer (~$16/mo) |
| AWS S3 + CloudFront | Frontend hosting ($0 always free) |
| AWS ECR | Docker image registry |
| AWS CloudWatch | Monitoring, logs, alarms |

### Testing

| Technology | Purpose |
|-----------|---------|
| JUnit 5 | Unit testing framework |
| Mockito | Mock dependencies in tests |
| TestContainers | Real DB in integration tests |
| REST Assured | HTTP API testing |
| JaCoCo | Code coverage reports |

---

## 4. Communication Flow Map

```
SERVICE-TO-SERVICE COMMUNICATION:

SYNCHRONOUS (HTTP — Feign Client):
  Payment Service ────HTTP────► Routing Service     ("Route this payment")
  Settlement Service ──HTTP──► Payment Service     ("Get captured payments")
  API Gateway ────────HTTP────► All services       (Route external traffic)

ASYNCHRONOUS (SQS — Fire and Forget):
  Payment Service ────SQS─────► Webhook Service    ("Deliver payment.captured event")
  Payment Service ────SQS─────► Notification Svc   ("Send confirmation email")
  Settlement Service ──SQS───► Webhook Service    ("Deliver settlement.processed")

TCP (Raw Binary Socket):
  Routing Service ────TCP─────► Bank Simulator     (ISO 8583 messages)

SERVICE DISCOVERY (Eureka):
  All Services ───heartbeat──► Eureka (every 30s)  ("I'm alive at this address")
  Gateway ────────lookup─────► Eureka              ("Where is PAYMENT-SERVICE?")

CONFIGURATION (Config Server):
  All Services ───on startup─► Config Server       ("Give me my application.yml")
```

---

## 5. Data Flow (Payment End-to-End)

```
1. Customer clicks "Pay ₹5000" on merchant's website
2. Merchant server calls: POST /v1/orders {amount: 5000}        → API Gateway
3. Gateway validates API key + rate limit                       → Routes to Payment Service
4. Payment Service creates order: ord_abc123 (expires in 30 min)
5. Customer enters card details on hosted checkout (port 3001)
6. Checkout calls: POST /v1/payments {orderId, card details}    → API Gateway
7. Gateway routes to Payment Service
8. Payment Service:
   a. Check idempotency key in Redis (prevent duplicate)
   b. Run fraud scoring (rules + ML = score 25 = APPROVE)
   c. Call Routing Service via Feign: POST /internal/route
9. Routing Service:
   a. Score available routes (HDFC: 97% success, ICICI: 89%)
   b. Pick best route: HDFC_ACQ_01
   c. Build ISO 8583 message (0100 Authorization Request)
   d. Encode to binary bytes
   e. Send via TCP to Bank Simulator (port 9000)
10. Bank Simulator:
    a. Receive ISO 8583 bytes
    b. Parse card number → 4111... → Rule: APPROVE
    c. Generate auth code: A1B2C3, RRN: 987654321012
    d. Build ISO 8583 response (0110)
    e. Send back via TCP
11. Routing Service decodes response → return to Payment Service
12. Payment Service:
    a. Save payment: status=AUTHORIZED, authCode=A1B2C3
    b. Mark order as PAID
    c. Publish "payment.authorized" to SQS
    d. Cache idempotency key in Redis (24h)
    e. Return PaymentResponse to customer
13. Webhook Service reads SQS → signs with HMAC → POST to merchant URL
14. Notification Service reads SQS → sends email via SNS
15. Customer sees "Payment Successful!" on checkout page
16. Later: Merchant calls POST /payments/{id}/capture → money moves
17. Midnight: Settlement Service runs batch → calculates fees → creates payout
```

---

## 6. Ports Reference

| Port | Service | Type | Access |
|------|---------|------|--------|
| 3000 | Merchant Dashboard (React) | HTTP | Browser |
| 3001 | Hosted Checkout (React) | HTTP | Browser |
| 3002 | Developer Portal (React) | HTTP | Browser |
| 5432 | PostgreSQL | TCP | Internal |
| 6379 | Redis | TCP | Internal |
| 8000 | DynamoDB Local | HTTP | Internal |
| 4566 | LocalStack (SQS/SNS) | HTTP | Internal |
| 8080 | API Gateway | HTTP | External (single entry) |
| 8081 | Identity Service | HTTP | Internal (via gateway) |
| 8082 | Merchant Service | HTTP | Internal (via gateway) |
| 8083 | Payment Service | HTTP | Internal (via gateway) |
| 8084 | Routing Service | HTTP | Internal (via payment-service) |
| 8085 | Settlement Service | HTTP | Internal (via gateway) |
| 8086 | Webhook Service | HTTP | Internal (via gateway) |
| 8087 | Notification Service | HTTP | Internal |
| 8761 | Eureka Dashboard | HTTP | Internal (dev access) |
| 8888 | Config Server | HTTP | Internal |
| 9000 | Bank Simulator | TCP | Internal (routing-service only) |

---

## 7. AWS Deployment Map

```
┌─────────────────────── AWS (ap-south-1, Mumbai) ──────────────────────────┐
│                                                                             │
│  ┌── VPC: 10.0.0.0/16 ─────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Public Subnets (10.0.1.0/24, 10.0.2.0/24):                         │  │
│  │  ├── ALB (Application Load Balancer) → routes to EC2                 │  │
│  │  └── Internet Gateway (connects to internet)                          │  │
│  │                                                                       │  │
│  │  Private Subnets (10.0.3.0/24, 10.0.4.0/24):                        │  │
│  │  ├── EC2 #1 (t3.micro): Docker with all services                     │  │
│  │  ├── RDS PostgreSQL (db.t3.micro)                                     │  │
│  │  └── ElastiCache Redis (cache.t3.micro)                               │  │
│  │                                                                       │  │
│  │  Security Groups:                                                     │  │
│  │  ├── ALB-SG: Allow 80/443 from internet                              │  │
│  │  ├── APP-SG: Allow 8080 from ALB only                                │  │
│  │  └── DB-SG: Allow 5432/6379 from APP only                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Managed Services (outside VPC):                                            │
│  ├── DynamoDB (3 tables)            $0/month                               │
│  ├── SQS (4 queues + 2 DLQs)       $0/month                               │
│  ├── SNS (2 topics)                 $0/month                               │
│  ├── S3 (frontend files)            $0/month                               │
│  ├── CloudFront (CDN)               $0/month                               │
│  └── CloudWatch (monitoring)        $0/month                               │
│                                                                             │
│  TOTAL COST: ~$52/month from $200 credits                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. CI/CD Pipeline Flow

```
DEVELOPER PUSHES CODE TO GITHUB:
     │
     ▼
┌────────────────────────────────────────────────────────────────┐
│   ci-backend.yml (triggers on push to main/develop)             │
│                                                                  │
│   Job 1: Build & Test                                           │
│   ├── Checkout code                                              │
│   ├── Setup Java 17                                              │
│   ├── Start PostgreSQL + Redis (service containers)              │
│   ├── mvn compile (all services)                                 │
│   ├── mvn test (unit tests)                                      │
│   ├── mvn verify (integration tests)                             │
│   └── JaCoCo coverage report                                     │
│                                                                  │
│   Job 2: Docker Build & Push (if main branch)                   │
│   ├── Build Docker image for each service                        │
│   ├── Push to GitHub Container Registry (ghcr.io)                │
│   └── Tag with: SHA + "latest"                                   │
│                                                                  │
│   Job 3: Security Scan                                           │
│   └── Trivy vulnerability scanner on images                      │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│   ci-frontend.yml (triggers on frontend-dashboard/** changes)   │
│                                                                  │
│   Job 1: Lint & Build                                           │
│   ├── npm ci (install dependencies)                              │
│   ├── npm run lint (ESLint)                                      │
│   ├── npm run build (Vite production build)                      │
│   └── Upload dist/ as artifact                                   │
│                                                                  │
│   Job 2: Deploy to S3 (if main branch)                          │
│   ├── Download build artifact                                    │
│   ├── aws s3 sync dist/ to bucket                                │
│   └── CloudFront cache invalidation                              │
└────────────────────────────────────────────────────────────────┘
```

---

## 9. Startup Command Reference

```cmd
# LOCAL DEVELOPMENT:

# Step 1: Start infrastructure
docker compose -f docker-compose-infra.yml up -d

# Step 2: Build all Java modules
mvn clean install -DskipTests

# Step 3: Start services (separate terminals)
cd service-registry   && mvn spring-boot:run   # Port 8761
cd config-server      && mvn spring-boot:run   # Port 8888
cd bank-simulator     && mvn spring-boot:run   # Port 9000
cd api-gateway        && mvn spring-boot:run   # Port 8080
cd identity-service   && mvn spring-boot:run   # Port 8081
cd merchant-service   && mvn spring-boot:run   # Port 8082
cd payment-service    && mvn spring-boot:run   # Port 8083
cd routing-service    && mvn spring-boot:run   # Port 8084
cd settlement-service && mvn spring-boot:run   # Port 8085
cd webhook-service    && mvn spring-boot:run   # Port 8086
cd notification-service && mvn spring-boot:run # Port 8087

# Step 4: Start frontends
cd frontend-dashboard && npm install && npm run dev        # Port 3000
cd frontend-checkout  && npm install && npm run dev        # Port 3001
cd frontend-developer-portal && npm install && npm run dev # Port 3002

# OR: Start everything with Docker:
docker compose up -d
```

---

## 10. AI Features

| # | Feature | Algorithm | Where Implemented | Input | Output |
|---|---------|-----------|-------------------|-------|--------|
| 1 | Smart Fraud Detection | Rule Engine + Decision Tree | payment-service (fraud scoring) | Transaction context (amount, time, device, velocity) | Risk score 0-100 + decision (APPROVE/CHALLENGE/REVIEW/DECLINE) |
| 2 | Smart Payment Routing | Multi-Armed Bandit (ε-greedy) | routing-service (RoutingEngine) | Card type, amount, time, route history | Best bank route (highest score) |
| 3 | Transaction Categorization | Keyword NLP Classifier | (optional: AWS Comprehend) | Merchant name/description | Category (Food, Travel, Shopping...) |
| 4 | Anomaly Detection | Z-Score Statistics | payment-service (future) | Current txn vs 30-day average | Normal / Unusual / Anomaly |
| 5 | Predictive Analytics | Moving Average + Trend | frontend-dashboard (charts) | 7-day payment volumes | Forecast next 7 days |
