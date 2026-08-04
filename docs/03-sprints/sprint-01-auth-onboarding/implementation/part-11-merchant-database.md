# Sprint 1, Part 11: Merchant Database

**Duration:** 2-3 hours  
**Prerequisites:** Part 10 completed, Merchant Service setup

---

## 1. What We're Building

In this part, you'll create the **database layer** for the Merchant Service.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT DATABASE SCHEMA                                 │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         SCHEMA: merchant                             │   │
│  │                                                                       │   │
│  │  ┌─────────────────────────┐          ┌─────────────────────┐       │   │
│  │  │       merchants         │          │      api_keys        │       │   │
│  │  ├─────────────────────────┤          ├─────────────────────┤       │   │
│  │  │ id (PK, VARCHAR 50)     │──────────│ id (PK, VARCHAR 50) │       │   │
│  │  │ user_id (VARCHAR 50)    │    1:N   │ merchant_id (FK)    │       │   │
│  │  │ business_name           │          │ key_type            │       │   │
│  │  │ business_type           │          │ public_key          │       │   │
│  │  │ registration_number     │          │ secret_key_hash     │       │   │
│  │  │ gst_number              │          │ key_prefix          │       │   │
│  │  │ website_url             │          │ status              │       │   │
│  │  │ callback_url            │          │ last_used_at        │       │   │
│  │  │ webhook_url             │          │ created_at          │       │   │
│  │  │ webhook_secret          │          └─────────────────────┘       │   │
│  │  │ settlement_schedule     │                                         │   │
│  │  │ mdr_percentage          │                                         │   │
│  │  │ bank_account_number     │                                         │   │
│  │  │ bank_ifsc_code          │                                         │   │
│  │  │ bank_account_holder     │                                         │   │
│  │  │ status                  │                                         │   │
│  │  │ kyc_verified            │                                         │   │
│  │  │ created_at              │                                         │   │
│  │  │ updated_at              │                                         │   │
│  │  └─────────────────────────┘                                         │   │
│  │                                                                       │   │
│  │  Key points:                                                          │   │
│  │  • ID is VARCHAR(50), not UUID                                       │   │
│  │  • user_id references identity.users (soft reference)                │   │
│  │  • One merchant has many API keys (TEST + LIVE)                      │   │
│  │  • Includes bank account for settlements                             │   │
│  │  • Includes MDR (Merchant Discount Rate) for fees                    │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Entity Design Decisions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT ENTITY DESIGN                                    │
│                                                                              │
│  Field                   │ Type           │ Why                             │
│  ────────────────────────┼────────────────┼──────────────────────────────── │
│  id                      │ VARCHAR(50)    │ 10-char generated ID (mrc_xxx) │
│  user_id                 │ VARCHAR(50)    │ Links to identity.users         │
│  business_name           │ VARCHAR(200)   │ Displayed on payment forms      │
│  business_type           │ VARCHAR(50)    │ INDIVIDUAL or COMPANY           │
│  registration_number     │ VARCHAR(100)   │ Company registration (optional) │
│  gst_number              │ VARCHAR(20)    │ GST/Tax ID (optional)           │
│  website_url             │ VARCHAR(500)   │ Business website                │
│  callback_url            │ VARCHAR(500)   │ Payment callback URL            │
│  webhook_url             │ VARCHAR(500)   │ Event notifications URL         │
│  webhook_secret          │ VARCHAR(255)   │ HMAC signing secret             │
│  settlement_schedule     │ VARCHAR(10)    │ T+2 (2 days after transaction) │
│  mdr_percentage          │ DECIMAL(5,2)   │ Merchant discount rate (2.00%) │
│  bank_account_number     │ VARCHAR(30)    │ For settlement payouts          │
│  bank_ifsc_code          │ VARCHAR(15)    │ Bank routing code               │
│  bank_account_holder     │ VARCHAR(200)   │ Name on bank account            │
│  status                  │ ENUM           │ PENDING, ACTIVE, SUSPENDED      │
│  kyc_verified            │ BOOLEAN        │ KYC verification status         │
│  created_at              │ TIMESTAMP      │ Audit trail                     │
│  updated_at              │ TIMESTAMP      │ Audit trail                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    STATUS LIFECYCLE                                │    │
│  │                                                                     │    │
│  │    PENDING ──────► ACTIVE ──────► SUSPENDED                        │    │
│  │       │              │                │                            │    │
│  │   (awaiting KYC)     │                │                            │    │
│  │                      ▼                ▼                            │    │
│  │                   ACTIVE ◄──── (can reactivate)                    │    │
│  │                                                                     │    │
│  │   PENDING: Just registered, awaiting KYC verification             │    │
│  │   ACTIVE: KYC passed, can process payments                        │    │
│  │   SUSPENDED: Temporarily blocked (compliance issue)                │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 API Key Security

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY STORAGE STRATEGY                                  │
│                                                                              │
│  PUBLIC KEY (pk_live_xxx, pk_test_xxx)                                      │
│  ─────────────────────────────────────                                      │
│  Stored: Plain text in database                                             │
│  Reason: Needs to be returned to merchant, used in frontend                 │
│  Risk: Low - can only create payment intents                                │
│                                                                              │
│  SECRET KEY (sk_live_xxx, sk_test_xxx)                                      │
│  ─────────────────────────────────────                                      │
│  Stored: HASHED (SHA-256 or bcrypt)                                         │
│  Reason: Has full API access, must be protected                             │
│  Shown: Only ONCE at creation, then never again                             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    KEY GENERATION FLOW                             │    │
│  │                                                                     │    │
│  │  1. Merchant requests new API key                                  │    │
│  │                                                                     │    │
│  │  2. Generate keys:                                                 │    │
│  │     publicKey  = "pk_live_" + SecureRandom(24 chars)               │    │
│  │     secretKey  = "sk_live_" + SecureRandom(24 chars)               │    │
│  │     keyPrefix  = secretKey.substring(0, 12) + "..."                │    │
│  │                                                                     │    │
│  │  3. Store in database:                                             │    │
│  │     public_key     = publicKey (plain)                             │    │
│  │     secret_key_hash = hash(secretKey)                              │    │
│  │     key_prefix     = "sk_live_abc1..." (for display)               │    │
│  │                                                                     │    │
│  │  4. Return to merchant (ONE TIME ONLY):                            │    │
│  │     { publicKey, secretKey }                                       │    │
│  │     ⚠️ IMPORTANT: Save this secret key - it won't be shown again! │    │
│  │                                                                     │    │
│  │  5. Merchant saves secretKey securely                              │    │
│  │     (We can NEVER retrieve it again!)                              │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Ensure PayFlow database exists with merchant schema
docker exec -it postgres psql -U payflow -d payflow -c "\dn"
# Should list schemas including: merchant

# If merchant schema doesn't exist, Flyway will create it
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Flyway Migration

**File: `merchant-service/src/main/resources/db/migration/V1__create_merchant_tables.sql`**

```sql
-- V1: Create merchant and api_keys tables

CREATE TABLE IF NOT EXISTS merchant.merchants (
    id                      VARCHAR(50) PRIMARY KEY,
    user_id                 VARCHAR(50) NOT NULL,
    business_name           VARCHAR(200) NOT NULL,
    business_type           VARCHAR(50) NOT NULL,
    registration_number     VARCHAR(100),
    gst_number              VARCHAR(20),
    website_url             VARCHAR(500),
    callback_url            VARCHAR(500),
    webhook_url             VARCHAR(500),
    webhook_secret          VARCHAR(255),
    settlement_schedule     VARCHAR(10) NOT NULL DEFAULT 'T+2',
    mdr_percentage          DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    bank_account_number     VARCHAR(30),
    bank_ifsc_code          VARCHAR(15),
    bank_account_holder     VARCHAR(200),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_user ON merchant.merchants(user_id);
CREATE INDEX idx_merchants_status ON merchant.merchants(status);

CREATE TABLE IF NOT EXISTS merchant.api_keys (
    id              VARCHAR(50) PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    key_type        VARCHAR(10) NOT NULL,
    public_key      VARCHAR(100) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,
    key_prefix      VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_public ON merchant.api_keys(public_key);
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(secret_key_hash);
```

**Key differences from Identity Service:**
- IDs are `VARCHAR(50)` not UUID - we use `IdGenerator.merchantId()` and `IdGenerator.apiKeyId()`
- Includes India-specific fields: `gst_number`, `bank_ifsc_code`
- Includes settlement fields: `settlement_schedule`, `mdr_percentage`
- Uses `kyc_verified` boolean instead of `kyc_status` enum

---

### Step 4.2: Create Merchant Entity

**File: `merchant-service/src/main/java/com/payflow/merchant/model/Merchant.java`**

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "settlement_schedule", length = 10, nullable = false)
    @Builder.Default
    private String settlementSchedule = "T+2";

    @Column(name = "mdr_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal mdrPercentage = new BigDecimal("2.00");

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
        PENDING, ACTIVE, SUSPENDED
    }
}
```

**Key points:**
- ID is `String` (not UUID) - generated by `IdGenerator.merchantId()`
- Status enum is **inner class** (not separate file)
- Uses `Instant` for timestamps (not `LocalDateTime`)
- Includes `schema = "merchant"` in `@Table` annotation
- Uses `@Data` (combines `@Getter`, `@Setter`, `@ToString`, etc.)

---

### Step 4.3: Create ApiKey Entity

**File: `merchant-service/src/main/java/com/payflow/merchant/model/ApiKey.java`**

```java
package com.payflow.merchant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_keys", schema = "merchant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 10)
    private KeyType keyType;

    @Column(name = "public_key", nullable = false, unique = true, length = 100)
    private String publicKey;

    @Column(name = "secret_key_hash", nullable = false, length = 255)
    private String secretKeyHash;

    @Column(name = "key_prefix", nullable = false, length = 30)
    private String keyPrefix;

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
        TEST, LIVE
    }

    public enum KeyStatus {
        ACTIVE, REVOKED
    }
}
```

**Key points:**
- `merchant_id` is a **String** (not object reference) - simple design
- No `@ManyToOne` relationship - we use explicit foreign key
- `KeyType` and `KeyStatus` are **inner enums**
- `key_prefix` stores masked version of secret key (e.g., `sk_live_abc1...`)

---

### Step 4.4: Create Repository Interfaces

**File: `merchant-service/src/main/java/com/payflow/merchant/repository/MerchantRepository.java`**

```java
package com.payflow.merchant.repository;

import com.payflow.merchant.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, String> {
    Optional<Merchant> findByUserId(String userId);
}
```

**Simple and focused:**
- Extends `JpaRepository<Merchant, String>` - note String ID type (not UUID)
- Single custom method: `findByUserId`
- Inherited methods: `save()`, `findById()`, `findAll()`, `delete()`

---

**File: `merchant-service/src/main/java/com/payflow/merchant/repository/ApiKeyRepository.java`**

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

    Optional<ApiKey> findBySecretKeyHashAndStatus(String secretKeyHash, ApiKey.KeyStatus status);

    Optional<ApiKey> findByPublicKey(String publicKey);
}
```

**Methods explained:**
- `findByMerchantIdAndStatus` - Get merchant's active API keys
- `findBySecretKeyHashAndStatus` - Authenticate API calls by secret key hash
- `findByPublicKey` - Look up key by public key (for payment forms)

---

## 5. Verification

### 5.1 Run Flyway Migration

```powershell
cd merchant-service
mvn spring-boot:run
```

Check migration applied:
```powershell
docker exec -it postgres psql -U payflow -d payflow -c "SELECT * FROM merchant.flyway_schema_history;"
```

### 5.2 Verify Tables Created

```powershell
docker exec -it postgres psql -U payflow -d payflow -c "\dt merchant.*"
```

Expected output:
```
        List of relations
 Schema   |      Name      | Type  | Owner   
----------+----------------+-------+---------
 merchant | api_keys       | table | payflow
 merchant | merchants      | table | payflow
```

---

## 6. File Structure

After completing this part:

```
merchant-service/
├── src/main/java/com/payflow/merchant/
│   ├── MerchantServiceApplication.java
│   ├── model/
│   │   ├── Merchant.java          ← Entity with inner MerchantStatus enum
│   │   └── ApiKey.java            ← Entity with inner KeyType, KeyStatus enums
│   └── repository/
│       ├── MerchantRepository.java
│       └── ApiKeyRepository.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__create_merchant_tables.sql
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ String ID Pattern                                                       │
│     • IDs are VARCHAR(50), not UUID                                        │
│     • Generated by IdGenerator.merchantId(), apiKeyId()                    │
│     • Format: mrc_xxx, key_xxx (10-char random)                            │
│                                                                              │
│  ✅ Inner Enum Pattern                                                      │
│     • Enums defined INSIDE entity class                                    │
│     • MerchantStatus, KeyType, KeyStatus                                   │
│     • Reference: Merchant.MerchantStatus, ApiKey.KeyType                   │
│                                                                              │
│  ✅ Schema Isolation                                                        │
│     • Table annotations include schema = "merchant"                        │
│     • Flyway creates schema automatically                                  │
│     • application.yml: flyway.schemas: merchant                            │
│                                                                              │
│  ✅ Simple Repository Design                                               │
│     • String IDs in JpaRepository<Entity, String>                         │
│     • Only essential methods defined                                       │
│     • No complex queries - keep it simple                                  │
│                                                                              │
│  ✅ Instant vs LocalDateTime                                               │
│     • We use Instant for timestamps                                        │
│     • Timezone-independent                                                 │
│     • Stored as UTC in database                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Common Issues & Solutions

### Issue: Schema 'merchant' does not exist

**Cause:** Flyway couldn't create the schema.

**Solution:** Check application.yml has:
```yaml
spring:
  flyway:
    schemas: merchant
    enabled: true
```

### Issue: Table already exists

**Cause:** Running migration twice or manually created tables.

**Solution:** Reset Flyway history:
```powershell
docker exec -it postgres psql -U payflow -d payflow -c "DROP SCHEMA merchant CASCADE;"
```
Then restart the application.

### Issue: Foreign key constraint failure

**Cause:** Trying to reference a non-existent merchant.

**Solution:** Ensure merchant exists before creating API keys.

---

## 9. Comparison with Identity Service

```
┌─────────────────────────────────────────────────────────────────────────────┐
│          IDENTITY SERVICE          │          MERCHANT SERVICE              │
├────────────────────────────────────┼────────────────────────────────────────┤
│ Schema: identity                   │ Schema: merchant                       │
│ Table: users                       │ Tables: merchants, api_keys           │
│ ID: String (usr_xxx)               │ ID: String (mrc_xxx, key_xxx)         │
│ Status: Inner enum in User         │ Status: Inner enum in Merchant        │
│ Timestamps: Instant                │ Timestamps: Instant                   │
│ Package: model (not entity)        │ Package: model (not entity)           │
│ Lombok: @Data                      │ Lombok: @Data                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ✅ Part 11 COMPLETE: Merchant Database                                     │
│                                                                              │
│  NEXT: Part 12 - Merchant Registration                                      │
│  ──────────────────────────────────────                                     │
│  Create merchant service layer and registration API.                        │
│                                                                              │
│  Continue to: part-12-merchant-registration.md                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 11 Complete!** 🎉
