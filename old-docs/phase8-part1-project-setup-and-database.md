# Hands-On Guide — Phase 8 Part 1: Settlement Service — Project Setup & Database

## Goal

By the end of Part 1, you will have:
- settlement-service Maven module running on port 8085
- Flyway migration creating `settlements` table in PostgreSQL
- Settlement JPA entity mapped to the table
- SettlementRepository interface for database access
- application.yml configured (DB, Eureka, Spring Batch)
- Service starts, connects to PostgreSQL, Flyway creates table
- Git commit

## Prerequisites

- Phase 7 completed (routing + bank simulator working)
- Docker infrastructure running (PostgreSQL has `settlement` schema)
- Understanding of what settlement does (from Phase 1 Part 1 domain knowledge)

---

## What Does the Settlement Service Do? (Recap)

```
EVERY NIGHT AT MIDNIGHT:

┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  SETTLEMENT JOB:                                                         │
│                                                                          │
│  1. FETCH: All CAPTURED payments from yesterday (24 hours of payments)   │
│                                                                          │
│  2. GROUP: By merchant (each merchant gets one settlement)               │
│     ├── Merchant A: 10 payments totaling ₹80,000                        │
│     ├── Merchant B: 5 payments totaling ₹25,000                         │
│     └── Merchant C: 20 payments totaling ₹1,50,000                      │
│                                                                          │
│  3. CALCULATE (for each merchant):                                       │
│     ├── Gross amount: ₹80,000                                           │
│     ├── Refunds: -₹5,000                                                │
│     ├── Net before fee: ₹75,000                                         │
│     ├── MDR fee (2%): -₹1,500                                           │
│     ├── GST on fee (18%): -₹270                                         │
│     └── Net payout: ₹73,230 ← merchant receives this                   │
│                                                                          │
│  4. SAVE: Settlement record in database                                  │
│                                                                          │
│  5. PAYOUT: Initiate bank transfer to merchant's account                 │
│                                                                          │
│  6. NOTIFY: Send webhook (settlement.processed) + email to merchant      │
│                                                                          │
│  7. UPDATE: Mark original payments as SETTLED                            │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Why Spring Batch for Settlement?

```
QUESTION: Why not just a @Scheduled method with a simple loop?

ANSWER: Because at scale, there could be MILLIONS of payments per day:

SIMPLE LOOP (bad for large scale):
  List<Payment> all = paymentRepo.findAllCapturedYesterday();
  // If 1 million payments → loads ALL into memory → OutOfMemoryError!
  // If server crashes at row 500,000 → starts from scratch next time!

SPRING BATCH (production-grade):
  ├── Processes in CHUNKS of 100 (only 100 in memory at a time)
  ├── CHECKPOINTS: If crash at chunk 5000, restart from chunk 5001 (not from scratch)
  ├── SKIP: If one payment has bad data, skip it, continue with rest
  ├── MONITORING: Know progress (processed 50,000 of 100,000)
  └── METRICS: Track how long each step took

For our demo project (small data): simple loop would work.
But we use Spring Batch to learn the production pattern.
Interview gold: "I used Spring Batch for chunked processing with restart capability."
```

---

## Step 1.1: Create pom.xml

**Create file:** `settlement-service/pom.xml`

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

    <artifactId>settlement-service</artifactId>
    <name>PayFlow Settlement Service</name>
    <description>Batch settlement processing, fee calculation, merchant payouts</description>

    <dependencies>
        <!-- Web: REST controllers + Swagger -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JPA: Database access (Settlement entity ↔ settlements table) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Batch: Chunked batch processing framework -->
        <!-- Gives us: Job, Step, ItemReader, ItemProcessor, ItemWriter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch</artifactId>
        </dependency>

        <!-- PostgreSQL driver -->
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

        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- OpenFeign: Call payment-service to get captured payments -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- Swagger UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- Common library -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- Actuator -->
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

**File:** `settlement-service/src/main/java/com/payflow/settlement/SettlementServiceApplication.java`

```java
package com.payflow.settlement;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication

@EnableFeignClients
// Allows calling payment-service via Feign to get captured payments

@EnableScheduling
// Enables @Scheduled methods (our midnight cron job)

@ComponentScan(basePackages = {"com.payflow.settlement", "com.payflow.common"})

@OpenAPIDefinition(info = @Info(
        title = "PayFlow Settlement Service API",
        version = "1.0",
        description = "Daily batch settlement, fee calculation, merchant payouts"
))
public class SettlementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
```

---

## Step 1.3: Create Flyway Migration

**Create file:** `settlement-service/src/main/resources/db/migration/V1__create_settlement_tables.sql`

```sql
-- =============================================================================
-- V1: Create settlement tables
-- Schema: settlement
-- =============================================================================

-- Main settlement record (one per merchant per day)
CREATE TABLE IF NOT EXISTS settlement.settlements (
    id                      VARCHAR(50) PRIMARY KEY,
    -- stl_Mn2kP9wQr5

    merchant_id             VARCHAR(50) NOT NULL,
    -- Which merchant this settlement is for

    settlement_date         DATE NOT NULL,
    -- The date these payments were captured (T-1)
    -- If settlement runs July 20 midnight, settlement_date = July 19

    -- AMOUNTS (all in ₹, 2 decimal places)
    gross_amount            DECIMAL(14,2) NOT NULL,
    -- Sum of all captured payment amounts for this day
    -- Example: ₹50,000 (from 15 transactions)

    refund_amount           DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    -- Sum of all refunds for this day (subtracted from gross)
    -- Example: ₹5,000 (2 refunds)

    fee_amount              DECIMAL(14,2) NOT NULL,
    -- MDR fee: (gross - refunds) × MDR% 
    -- Example: ₹45,000 × 2% = ₹900

    gst_on_fee              DECIMAL(14,2) NOT NULL,
    -- GST on MDR: fee_amount × 18%
    -- Example: ₹900 × 18% = ₹162

    net_amount              DECIMAL(14,2) NOT NULL,
    -- What merchant actually receives: gross - refunds - fee - gst
    -- Example: ₹50,000 - ₹5,000 - ₹900 - ₹162 = ₹43,938

    -- COUNTS
    total_transactions      INTEGER NOT NULL DEFAULT 0,
    total_refunds           INTEGER NOT NULL DEFAULT 0,

    -- STATUS
    status                  VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    -- INITIATED: Created, not yet processed
    -- PROCESSING: Batch job running
    -- PROCESSED: Amounts calculated
    -- COMPLETED: Payout sent to merchant's bank
    -- FAILED: Something went wrong

    payout_utr              VARCHAR(50),
    -- UTR (Unique Transaction Reference) from bank when payout is processed
    -- Example: "HDFC2026072000456"
    -- This proves money was actually sent

    processed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    -- One settlement per merchant per day (no duplicates)
    UNIQUE(merchant_id, settlement_date)
);

CREATE INDEX idx_settlements_merchant ON settlement.settlements(merchant_id);
CREATE INDEX idx_settlements_date ON settlement.settlements(settlement_date DESC);
CREATE INDEX idx_settlements_status ON settlement.settlements(status);
```

---

## Step 1.4: Create Settlement Entity

**File:** `settlement-service/src/main/java/com/payflow/settlement/model/Settlement.java`

```java
package com.payflow.settlement.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "settlements", schema = "settlement")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Settlement {

    @Id
    @Column(length = 50)
    private String id;
    // stl_Mn2kP9wQr5

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;
    // The date payments were captured (yesterday)

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "refund_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "gst_on_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal gstOnFee;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;
    // This is what the merchant actually gets in their bank

    @Column(name = "total_transactions")
    @Builder.Default
    private int totalTransactions = 0;

    @Column(name = "total_refunds")
    @Builder.Default
    private int totalRefunds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.INITIATED;

    @Column(name = "payout_utr", length = 50)
    private String payoutUtr;
    // Bank's reference number for the payout transfer

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SettlementStatus {
        INITIATED,   // Record created
        PROCESSING,  // Batch job calculating
        PROCESSED,   // Amounts finalized
        COMPLETED,   // Payout sent to merchant bank
        FAILED       // Error occurred
    }
}
```

---

## Step 1.5: Create Repository

**File:** `settlement-service/src/main/java/com/payflow/settlement/repository/SettlementRepository.java`

```java
package com.payflow.settlement.repository;

import com.payflow.settlement.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, String> {

    List<Settlement> findByMerchantIdOrderBySettlementDateDesc(String merchantId);
    // "Get all settlements for merchant X, newest first"
    // Used by: Merchant dashboard → settlements page

    Optional<Settlement> findByMerchantIdAndSettlementDate(String merchantId, LocalDate date);
    // "Get settlement for merchant X on date Y"
    // Used to check: was today's settlement already run? (prevent duplicates)

    List<Settlement> findByStatus(Settlement.SettlementStatus status);
    // "Get all settlements that are still PROCESSING"
    // Used to: resume interrupted batch jobs
}
```

---

## Step 1.6: Create application.yml

**File:** `settlement-service/src/main/resources/application.yml`

```yaml
server:
  port: 8085
  # Settlement service runs on port 8085

spring:
  application:
    name: settlement-service

  # ----- Database -----
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=settlement
    username: payflow
    password: payflow_secret

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        default_schema: settlement
        format_sql: true

  flyway:
    enabled: true
    schemas: settlement
    locations: classpath:db/migration

  # ----- Spring Batch -----
  batch:
    jdbc:
      initialize-schema: always
      # Spring Batch needs its OWN metadata tables to track job progress:
      # BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION
      # "always" creates them automatically on first run

# ----- Eureka -----
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# ----- Swagger -----
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## Step 1.7: Verify

### Build:
```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests -pl common-lib,settlement-service -am
```
Expected: `BUILD SUCCESS`

### Run:
```cmd
cd settlement-service
mvn spring-boot:run
```

### Check console for:
```
Flyway: Migrating schema "settlement" to version "1 - create settlement tables"
Started SettlementServiceApplication in X.xxx seconds
```

### Verify table:
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt settlement.*"
```
Expected: `settlements` table listed.

### Swagger:
http://localhost:8085/swagger-ui.html

---

## Step 1.8: Git Commit

```cmd
git add settlement-service/
git commit -m "Phase 8 Part 1: Settlement service - setup, entity, repository, flyway migration"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `pom.xml` | Dependencies (web, JPA, Batch, Feign, Flyway) |
| `SettlementServiceApplication.java` | Main + @EnableScheduling + @EnableFeignClients |
| `model/Settlement.java` | Entity → settlements table |
| `repository/SettlementRepository.java` | DB access methods |
| `application.yml` | Port 8085, DB, Batch config, Eureka |
| `V1__create_settlement_tables.sql` | Table DDL with indexes |

---

## Interview Notes

**Q: "Why a separate service for settlement?"**
> "Settlement runs heavy batch processing (millions of records) at midnight. If it ran inside payment-service, it would steal resources from real-time payment processing (which needs low latency). As a separate service, it has its own resources and can be scaled independently. If settlement fails, payments keep working."

---

## Next Step

→ Continue to **Phase 8 Part 2: Fee Calculation**
