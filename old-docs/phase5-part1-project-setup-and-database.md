# Hands-On Guide — Phase 5 Part 1: Merchant Service — Project Setup & Database

## Goal

By the end of Part 1, you will have:
- merchant-service Maven module with all dependencies
- Flyway migration creating `merchants` and `api_keys` tables
- Merchant and ApiKey JPA entity classes
- MerchantRepository and ApiKeyRepository interfaces
- application.yml configured for local development
- Service starts and connects to PostgreSQL (merchant schema)
- Git commit

## Prerequisites

- Phase 4 completed (identity-service fully working)
- Docker infrastructure running (`docker compose -f docker-compose-infra.yml up -d`)
- PostgreSQL has `merchant` schema (created by docker/init-db.sql)

---

## How Merchant Service Fits in the Architecture

```
Merchant registers on dashboard → Identity Service (creates user account)
                                → Merchant Service (creates merchant profile + API keys)

Later, merchant makes API calls:
  Merchant Server → API Gateway (8080) → validates API key → routes to Payment Service

Merchant Service responsibilities:
├── Store merchant business details (name, GST, bank account)
├── Generate API key pairs (pk_pay_xxx + sk_pay_xxx)
├── Store webhook URL and secret (for webhook delivery)
├── Configure fee plans (MDR percentage per payment method)
└── Provide merchant lookup for other services
```

---

## Step 1.1: Create pom.xml

**Create file:** `merchant-service/pom.xml`

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
        <!-- Inherits Java 17, Spring Boot 3.2.5, all version management -->
    </parent>

    <artifactId>merchant-service</artifactId>
    <name>PayFlow Merchant Service</name>
    <description>Merchant onboarding, API key management, fee configuration</description>

    <dependencies>
        <!-- Web: REST controllers + embedded Tomcat -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JPA: Database access via Hibernate ORM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL JDBC driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway: Database migration (versioned SQL scripts) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Validation: @NotBlank, @Email, @Size on request DTOs -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Eureka Client: Register with service discovery -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Swagger UI: Auto-generated API documentation -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- Our shared library: ApiResponse, exceptions, IdGenerator -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- Actuator: /actuator/health endpoint -->
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

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/MerchantServiceApplication.java`

```java
package com.payflow.merchant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// Enables auto-configuration, component scanning, and bean definition

@ComponentScan(basePackages = {"com.payflow.merchant", "com.payflow.common"})
// Scan both our package AND common-lib (picks up GlobalExceptionHandler)

@OpenAPIDefinition(info = @Info(
        title = "PayFlow Merchant Service API",
        version = "1.0",
        description = "Merchant onboarding, API key management, and fee configuration"
))
// Configures Swagger UI page header
// Visit: http://localhost:8082/swagger-ui.html

public class MerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
        // Starts embedded Tomcat on port 8082
        // Connects to PostgreSQL (merchant schema)
        // Runs Flyway migrations
        // Registers with Eureka
    }
}
```

---

## Step 1.3: Create Flyway Migration

**What this does:** Creates the `merchants` and `api_keys` tables on first startup.

**Create file:** `merchant-service/src/main/resources/db/migration/V1__create_merchant_tables.sql`

```sql
-- =============================================================================
-- V1: Create merchant tables
-- This runs automatically via Flyway on first startup
-- Schema: merchant (set in application.yml)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: merchants
-- Stores business information for each merchant (one per user account)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant.merchants (
    -- Primary key: our custom short ID (merch_Hk7mN3xQp2)
    id                      VARCHAR(50) PRIMARY KEY,

    -- Links to identity.users table (which user owns this merchant)
    -- Not a foreign key (cross-schema) — validated in application code
    user_id                 VARCHAR(50) NOT NULL,

    -- Business details (provided during onboarding)
    business_name           VARCHAR(200) NOT NULL,
    business_type           VARCHAR(50) NOT NULL,
    -- business_type: INDIVIDUAL, PARTNERSHIP, COMPANY, LLP

    registration_number     VARCHAR(100),
    -- CIN for companies, PAN for individuals

    gst_number              VARCHAR(20),
    -- GSTIN: 27AABCU9603R1ZM (15 characters)

    website_url             VARCHAR(500),
    -- Merchant's website (optional, for verification)

    -- Payment integration URLs
    callback_url            VARCHAR(500),
    -- Where to redirect customer after checkout (success/failure)

    webhook_url             VARCHAR(500),
    -- Where we POST webhook events (payment.captured, refund.created, etc.)

    webhook_secret          VARCHAR(255),
    -- Random 32-char string used to HMAC-sign webhook payloads
    -- Merchant uses this to verify webhooks are really from us

    -- Settlement configuration
    settlement_schedule     VARCHAR(10) NOT NULL DEFAULT 'T+2',
    -- T+1: money next day, T+2: money in 2 days, T+3: 3 days, WEEKLY

    -- Fee configuration (default — can be overridden per payment method)
    mdr_percentage          DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    -- MDR: 2% of each transaction amount is our fee
    -- Example: ₹1000 payment → merchant gets ₹980, we get ₹20

    -- Bank account (for settlement payouts)
    bank_account_number     VARCHAR(30),
    bank_ifsc_code          VARCHAR(15),
    -- IFSC: HDFC0001234 (identifies specific bank branch)
    bank_account_holder     VARCHAR(200),

    -- Status
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING: Just registered, awaiting KYC verification
    -- ACTIVE: KYC verified, can accept payments
    -- SUSPENDED: Blocked by admin (fraud, compliance issue)

    kyc_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    -- Set to true after admin verifies documents

    -- Audit timestamps
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index: Find merchant by user account (one-to-one relationship)
CREATE INDEX idx_merchants_user ON merchant.merchants(user_id);

-- Index: Filter by status (admin dashboard: "show all pending merchants")
CREATE INDEX idx_merchants_status ON merchant.merchants(status);


-- -----------------------------------------------------------------------------
-- Table: api_keys
-- Stores API key pairs for merchant authentication
-- Each merchant can have multiple keys (test + live, rotated keys)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant.api_keys (
    -- Primary key
    id              VARCHAR(50) PRIMARY KEY,

    -- Which merchant owns this key
    merchant_id     VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    -- ON DELETE: don't cascade (revoking merchant doesn't auto-delete keys for audit)

    -- Key type
    key_type        VARCHAR(10) NOT NULL,
    -- TEST: For development (no real transactions)
    -- LIVE: For production (real money)

    -- The public key (shown in dashboard, safe to expose)
    -- Format: pk_tst_51a2b3c4d5e6f7g8h9 or pk_pay_...
    public_key      VARCHAR(100) NOT NULL UNIQUE,

    -- SHA-256 hash of the secret key
    -- We NEVER store the actual secret key!
    -- On API call: hash incoming key → look up this column
    secret_key_hash VARCHAR(255) NOT NULL,

    -- First 12 characters of secret key (for identification in logs)
    -- "sk_pay_9h8g7f..." — enough to identify without exposing full key
    key_prefix      VARCHAR(30) NOT NULL,

    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE: Can be used for API calls
    -- REVOKED: Disabled (merchant rotated keys or compromised)

    -- Tracking
    last_used_at    TIMESTAMP,
    -- Updated every time this key is used (helps identify unused keys)

    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index: Find all keys for a merchant (dashboard: "show my API keys")
CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);

-- Index: Look up by public key (fast lookup during request processing)
CREATE INDEX idx_api_keys_public ON merchant.api_keys(public_key);

-- Index: Look up by secret key hash (authentication: find merchant from API key)
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(secret_key_hash);
```

---

## Step 1.4: Create Merchant Entity

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/model/Merchant.java`

```java
package com.payflow.merchant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "merchants", schema = "merchant")
// Maps to: merchant.merchants table in PostgreSQL

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Merchant {

    @Id
    @Column(length = 50)
    private String id;
    // Our custom ID: merch_Hk7mN3xQp2 (generated by IdGenerator.merchantId())

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
    // Which user from identity-service owns this merchant
    // Used to link: "this merchant belongs to user usr_xyz"

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;
    // INDIVIDUAL, PARTNERSHIP, COMPANY, LLP

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;
    // Redirect URL after checkout (where customer goes after paying)

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    // Where we deliver webhook events (merchant's server endpoint)

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;
    // 32-char random string for HMAC-SHA256 signing of webhooks

    @Column(name = "settlement_schedule", length = 10, nullable = false)
    @Builder.Default
    private String settlementSchedule = "T+2";

    @Column(name = "mdr_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal mdrPercentage = new BigDecimal("2.00");
    // BigDecimal for money: NEVER use float/double for financial calculations!
    // float: 0.1 + 0.2 = 0.30000000000000004 (WRONG for money)
    // BigDecimal: 0.1 + 0.2 = 0.3 (CORRECT)

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc_code", length = 15)
    private String bankIfscCode;

    @Column(name = "bank_account_holder", length = 200)
    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING;

    @Column(name = "kyc_verified", nullable = false)
    @Builder.Default
    private boolean kycVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MerchantStatus {
        PENDING,    // Just registered, awaiting KYC
        ACTIVE,     // KYC verified, can accept payments
        SUSPENDED   // Blocked by admin
    }
}
```

---

## Step 1.5: Create ApiKey Entity

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/model/ApiKey.java`

```java
package com.payflow.merchant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_keys", schema = "merchant")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiKey {

    @Id
    @Column(length = 50)
    private String id;
    // key_Hk7mN3xQp2

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 10)
    private KeyType keyType;

    @Column(name = "public_key", nullable = false, unique = true, length = 100)
    private String publicKey;
    // pk_tst_51a2b3c4d5e6f7g8h9 (safe to show in dashboard)

    @Column(name = "secret_key_hash", nullable = false, length = 255)
    private String secretKeyHash;
    // SHA-256 hash of sk_tst_... (NEVER store actual secret!)

    @Column(name = "key_prefix", nullable = false, length = 30)
    private String keyPrefix;
    // First 12 chars of secret (for identification in logs)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KeyStatus status = KeyStatus.ACTIVE;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum KeyType {
        TEST,   // Development/testing (no real transactions)
        LIVE    // Production (real money)
    }

    public enum KeyStatus {
        ACTIVE,  // Can be used
        REVOKED  // Disabled (rotated or compromised)
    }
}
```

---

## Step 1.6: Create Repository Interfaces

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/repository/MerchantRepository.java`

```java
package com.payflow.merchant.repository;

import com.payflow.merchant.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
// Spring Data JPA auto-implements this interface at runtime
// No implementation class needed! Spring generates SQL from method names.
public interface MerchantRepository extends JpaRepository<Merchant, String> {
    // JpaRepository<Merchant, String>:
    //   Merchant = entity type
    //   String = primary key type (our custom String IDs)
    //
    // Inherited for free: save(), findById(), findAll(), delete(), count()

    Optional<Merchant> findByUserId(String userId);
    // Spring generates: SELECT * FROM merchant.merchants WHERE user_id = ?
    // Returns Optional: empty if not found (avoids NullPointerException)
}
```

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/repository/ApiKeyRepository.java`

```java
package com.payflow.merchant.repository;

import com.payflow.merchant.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    List<ApiKey> findByMerchantIdAndStatus(String merchantId, ApiKey.KeyStatus status);
    // SELECT * FROM merchant.api_keys WHERE merchant_id = ? AND status = ?
    // Used for: "Show all active keys for this merchant"

    Optional<ApiKey> findBySecretKeyHashAndStatus(String secretKeyHash, ApiKey.KeyStatus status);
    // SELECT * FROM merchant.api_keys WHERE secret_key_hash = ? AND status = 'ACTIVE'
    // Used for: API key authentication (hash incoming key, find matching record)

    Optional<ApiKey> findByPublicKey(String publicKey);
    // SELECT * FROM merchant.api_keys WHERE public_key = ?
    // Used for: Look up key details in dashboard
}
```

---

## Step 1.7: Create application.yml

**Create file:** `merchant-service/src/main/resources/application.yml`

```yaml
server:
  port: 8082
  # Merchant service runs on port 8082

spring:
  application:
    name: merchant-service
    # Registers as "MERCHANT-SERVICE" in Eureka
    # API Gateway routes /v1/merchants/** here

  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=merchant
    # Same PostgreSQL, different schema (merchant)
    username: payflow
    password: payflow_secret

  jpa:
    hibernate:
      ddl-auto: validate
      # Validate entities match tables (Flyway creates tables)
    show-sql: true
    properties:
      hibernate:
        default_schema: merchant
        format_sql: true

  flyway:
    enabled: true
    schemas: merchant
    locations: classpath:db/migration

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## Step 1.8: Verify

### 1. Build:
```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests -pl common-lib,merchant-service -am
```
Expected: `BUILD SUCCESS`

### 2. Run:
```cmd
cd merchant-service
mvn spring-boot:run
```

### 3. Check console for:
```
Flyway: Migrating schema "merchant" to version "1 - create merchant tables"
Started MerchantServiceApplication in 4.xxx seconds
```

### 4. Verify tables:
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt merchant.*"
```
Expected: `merchants` and `api_keys` tables listed.

### 5. Swagger UI:
Open http://localhost:8082/swagger-ui.html (empty for now — controllers in Part 2)

---

## Step 1.9: Git Commit

```cmd
git add merchant-service/
git commit -m "Phase 5 Part 1: Merchant service - setup, entities, flyway migration"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `merchant-service/pom.xml` | Dependencies (web, JPA, Flyway, Eureka, Swagger) |
| `MerchantServiceApplication.java` | Main class + Swagger config |
| `model/Merchant.java` | JPA entity → merchants table |
| `model/ApiKey.java` | JPA entity → api_keys table |
| `repository/MerchantRepository.java` | DB access for merchants |
| `repository/ApiKeyRepository.java` | DB access for API keys |
| `resources/application.yml` | Port 8082, DB, Eureka config |
| `db/migration/V1__create_merchant_tables.sql` | Table creation SQL |

---

## Next Step

→ Continue to **Phase 5 Part 2: Merchant Onboarding**
