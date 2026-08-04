# Hands-On Guide — Phase 6 Part 1: Payment Service — Project Setup & Database

## Goal

By the end of Part 1, you will have:
- payment-service Maven module with dependencies (web, JPA, Redis, Feign, Resilience4j)
- Flyway migrations creating `orders`, `payments`, and `refunds` tables
- Order, Payment, Refund JPA entity classes
- PaymentRepository with custom query methods
- application.yml with PostgreSQL + Redis + Eureka config
- Service starts and tables are created
- Git commit

## Prerequisites

- Phase 5 completed (merchant-service working, API keys can be generated)
- Docker running (PostgreSQL + Redis needed)
- PostgreSQL has `payment` schema (from docker/init-db.sql)

---

## Why This is the Most Important Service

```
Payment Service is the CORE of the entire platform.
Every other service exists to support this one:

├── identity-service → authenticates users WHO make payments
├── merchant-service → identifies WHICH merchant is accepting payment
├── routing-service → routes payment TO the bank
├── settlement-service → settles captured payments TO merchant
├── webhook-service → notifies merchant ABOUT payment events
└── notification-service → notifies customer ABOUT their payment

Payment Service owns the payment LIFECYCLE:
  CREATED → PROCESSING → AUTHORIZED → CAPTURED → SETTLED
                        → FAILED
           AUTHORIZED → VOIDED / EXPIRED
           CAPTURED → REFUNDED
```

---

## Step 1.1: Create pom.xml

**Create file:** `payment-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>payment-service</artifactId>
    <name>PayFlow Payment Service</name>
    <description>Core payment processing - orders, authorization, capture, refund</description>

    <dependencies>
        <!-- ===== Core Spring ===== -->

        <!-- REST controllers + embedded Tomcat -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Database access (Hibernate ORM) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Redis: idempotency keys, rate limiting -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Input validation (@NotNull, @DecimalMin, etc.) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- ===== Database ===== -->

        <!-- PostgreSQL JDBC driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway (DB migrations) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- ===== Spring Cloud ===== -->

        <!-- Eureka Client (service discovery) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- OpenFeign: Declarative REST client for calling other services -->
        <!-- Used to call: routing-service, merchant-service -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- ===== Resilience ===== -->

        <!-- Circuit breaker, retry, rate limiter -->
        <!-- If routing-service is down, don't keep trying (fail fast) -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
        </dependency>

        <!-- ===== API Docs ===== -->

        <!-- Swagger UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- ===== Shared ===== -->

        <!-- Our common library (ApiResponse, exceptions, IdGenerator, enums) -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- Health check endpoints -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Step 1.2: Create Main Application Class

**Create file:** `payment-service/src/main/java/com/payflow/payment/PaymentServiceApplication.java`

```java
package com.payflow.payment;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication

@EnableFeignClients
// @EnableFeignClients: Activates Feign HTTP clients
// Feign lets us call other services with just an interface:
//   @FeignClient("ROUTING-SERVICE")
//   public interface RoutingClient {
//       @PostMapping("/internal/route")
//       RouteResponse route(@RequestBody RouteRequest request);
//   }
// Spring auto-generates the HTTP call code!

@ComponentScan(basePackages = {"com.payflow.payment", "com.payflow.common"})
// Scan both packages (picks up GlobalExceptionHandler from common-lib)

@OpenAPIDefinition(info = @Info(
        title = "PayFlow Payment Service API",
        version = "1.0",
        description = "Payment lifecycle - create orders, authorize, capture, void, refund"
))
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

---

## Step 1.3: Create Flyway Migration

**Create file:** `payment-service/src/main/resources/db/migration/V1__create_payment_tables.sql`

```sql
-- =============================================================================
-- V1: Create payment tables (orders, payments, refunds)
-- Schema: payment
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: orders
-- Represents a payment intent created by merchant BEFORE customer pays.
-- Think of it like a shopping cart checkout — amount is decided, waiting for payment.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment.orders (
    id              VARCHAR(50) PRIMARY KEY,
    -- ord_LkR3d9xF2m

    merchant_id     VARCHAR(50) NOT NULL,
    -- Which merchant created this order

    amount          DECIMAL(12,2) NOT NULL,
    -- Payment amount (e.g., 5000.00 for ₹5000)
    -- DECIMAL(12,2): up to 9,99,99,99,999.99 (sufficient for any transaction)
    -- NEVER use FLOAT for money! (floating point errors with money = lawsuits)

    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    -- ISO 4217 currency code (INR, USD, EUR)

    receipt         VARCHAR(100),
    -- Merchant's internal order reference (e.g., "ORDER-12345")
    -- Helps merchant correlate our payment with their order system

    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    -- CREATED: Waiting for payment
    -- PAID: Payment successful (linked to a payment record)
    -- EXPIRED: Customer didn't pay within 30 minutes

    notes           JSONB,
    -- Flexible key-value metadata (product name, customer email, etc.)
    -- JSONB: PostgreSQL binary JSON (fast queries, indexable)

    expires_at      TIMESTAMP NOT NULL,
    -- Auto-expire time (created_at + 30 minutes)
    -- If customer doesn't pay by this time → status changes to EXPIRED

    paid_at         TIMESTAMP,
    -- When payment was successfully made (null until paid)

    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_merchant ON payment.orders(merchant_id);
CREATE INDEX idx_orders_status ON payment.orders(status);
-- Partial index: only find non-expired CREATED orders (used by expiry scheduler)
CREATE INDEX idx_orders_expires ON payment.orders(expires_at) WHERE status = 'CREATED';


-- -----------------------------------------------------------------------------
-- Table: payments
-- The actual payment transaction record.
-- Created when customer submits their card/UPI details.
-- One order can have multiple payment attempts (retry after decline).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment.payments (
    id                  VARCHAR(50) PRIMARY KEY,
    -- pay_Hk7mN3xQp2

    order_id            VARCHAR(50) NOT NULL REFERENCES payment.orders(id),
    -- Links to the order this payment is for

    merchant_id         VARCHAR(50) NOT NULL,
    -- Denormalized (also in order) for fast queries without join

    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',

    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    -- CREATED → PROCESSING → AUTHORIZED → CAPTURED → SETTLED
    --                       → FAILED
    --           AUTHORIZED → VOIDED / EXPIRED
    --           CAPTURED   → REFUNDED

    payment_method      VARCHAR(20) NOT NULL,
    -- CARD, UPI, NETBANKING, WALLET

    -- Card details (ONLY last4 stored — never full PAN! PCI compliance)
    card_last4          VARCHAR(4),
    card_network        VARCHAR(20),   -- VISA, MASTERCARD, RUPAY

    -- UPI details
    upi_vpa             VARCHAR(100),  -- rajesh@okicici

    -- Bank response (populated after authorization)
    auth_code           VARCHAR(10),   -- A1B2C3 (proof bank approved)
    rrn                 VARCHAR(20),   -- 987654321012 (bank's reference number)

    -- Idempotency (duplicate prevention)
    idempotency_key     VARCHAR(100),
    -- Unique key per payment attempt — prevents double-charging

    -- Fraud detection
    risk_score          INTEGER,       -- 0-100 from fraud engine
    route_id            VARCHAR(50),   -- Which bank route was used (HDFC_ACQ_01)

    -- Amount tracking
    captured_amount     DECIMAL(12,2) DEFAULT 0.00,
    -- How much has been captured (can be less than authorized — partial capture)
    refunded_amount     DECIMAL(12,2) DEFAULT 0.00,
    -- How much has been refunded back to customer

    -- Error info (if payment failed)
    failure_code        VARCHAR(50),
    failure_reason      VARCHAR(500),

    -- Timestamps
    authorized_at       TIMESTAMP,     -- When bank approved
    captured_at         TIMESTAMP,     -- When merchant captured
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payment.payments(order_id);
CREATE INDEX idx_payments_merchant ON payment.payments(merchant_id);
CREATE INDEX idx_payments_status ON payment.payments(status);
-- Composite index for idempotency lookup (fast duplicate check)
CREATE INDEX idx_payments_idempotency ON payment.payments(merchant_id, idempotency_key);


-- -----------------------------------------------------------------------------
-- Table: refunds
-- Records of money returned to customer after capture.
-- A payment can have multiple partial refunds.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment.refunds (
    id              VARCHAR(50) PRIMARY KEY,
    -- rfnd_Qm4nP8wXv3

    payment_id      VARCHAR(50) NOT NULL REFERENCES payment.payments(id),
    merchant_id     VARCHAR(50) NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,

    status          VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    -- INITIATED → PROCESSED (success) or FAILED

    reason          VARCHAR(500),
    -- "Customer returned product", "Order cancelled", etc.

    rrn             VARCHAR(20),
    -- Bank reference for the refund transaction

    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP
);

CREATE INDEX idx_refunds_payment ON payment.refunds(payment_id);
CREATE INDEX idx_refunds_merchant ON payment.refunds(merchant_id);
```

---

## Step 1.4: Create application.yml

**Create file:** `payment-service/src/main/resources/application.yml`

```yaml
server:
  port: 8083
  # Payment service on port 8083

spring:
  application:
    name: payment-service

  # ----- PostgreSQL -----
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=payment
    username: payflow
    password: payflow_secret

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        default_schema: payment
        format_sql: true

  flyway:
    enabled: true
    schemas: payment
    locations: classpath:db/migration

  # ----- Redis (for idempotency + caching) -----
  data:
    redis:
      host: localhost
      port: 6379
      # Redis stores:
      # 1. Idempotency keys (TTL 24h) — prevent duplicate payments
      # 2. Rate limit counters (TTL 1min) — prevent abuse
      # 3. Cached merchant data (TTL 1h) — avoid DB lookup every request

# ----- Eureka -----
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# ----- Swagger -----
springdoc:
  swagger-ui:
    path: /swagger-ui.html

# ----- Payment Configuration -----
payment:
  order-expiry-minutes: 30
  # Orders expire 30 minutes after creation if not paid
  idempotency-key-ttl-hours: 24
  # Idempotency keys cached in Redis for 24 hours
```

---

## Step 1.5: Verify

### Build:
```cmd
mvn clean install -DskipTests -pl common-lib,payment-service -am
```

### Run:
```cmd
cd payment-service
mvn spring-boot:run
```

### Check console:
```
Flyway: Migrating schema "payment" to version "1 - create payment tables"
Started PaymentServiceApplication in 5.xxx seconds
```

### Verify tables:
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt payment.*"
```
Expected: `orders`, `payments`, `refunds` listed.

### Swagger:
http://localhost:8083/swagger-ui.html (empty — controllers in Part 3+)

---

## Step 1.6: Git Commit

```cmd
git add payment-service/
git commit -m "Phase 6 Part 1: Payment service - setup, entities, flyway migration (orders, payments, refunds)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `payment-service/pom.xml` | Dependencies (web, JPA, Redis, Feign, Resilience4j, Swagger) |
| `PaymentServiceApplication.java` | Main + @EnableFeignClients |
| `resources/application.yml` | Port 8083, DB, Redis, Eureka |
| `db/migration/V1__create_payment_tables.sql` | orders + payments + refunds tables |

**Note:** Entity classes (Payment.java, Order.java) already exist from earlier source code generation. If not, create them following the same pattern as Merchant entity.

---

## Next Step

→ Continue to **Phase 6 Part 2: Payment State Machine**

In Part 2 we implement the state machine — the rules that govern which transitions are valid (can't capture a voided payment, can't void after capture, etc.)
