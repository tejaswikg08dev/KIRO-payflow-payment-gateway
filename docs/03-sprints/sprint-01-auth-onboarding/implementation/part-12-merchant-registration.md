# Sprint 1, Part 12: Merchant Registration

**Duration:** 2-3 hours  
**Prerequisites:** Part 11 completed, Merchant database tables created

---

## 1. What We're Building

In this part, you'll implement the **merchant registration flow** with API key generation.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT REGISTRATION FLOW                               │
│                                                                              │
│  User Journey:                                                               │
│  ─────────────                                                              │
│                                                                              │
│  1. User registers via Identity Service                                     │
│     POST /v1/auth/register                                                  │
│          ↓                                                                   │
│     Returns: JWT token + user_id                                            │
│                                                                              │
│  2. User creates merchant via Merchant Service                              │
│     POST /v1/merchants                                                      │
│     Body: { userId, businessName, businessType, ... }                       │
│          ↓                                                                   │
│     Returns: Merchant info (wrapped in ApiResponse)                         │
│                                                                              │
│  3. User generates API keys (separate request)                              │
│     POST /v1/merchants/{merchantId}/api-keys                                │
│          ↓                                                                   │
│     Returns: API key pair (secret shown ONCE)                               │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    API KEY RESPONSE EXAMPLE                        │    │
│  │                                                                     │    │
│  │  {                                                                  │    │
│  │    "success": true,                                                │    │
│  │    "data": {                                                       │    │
│  │      "key_id": "key_abc12345",                                    │    │
│  │      "key_type": "TEST",                                          │    │
│  │      "public_key": "pk_test_abc123...",                           │    │
│  │      "secret_key": "sk_test_xyz789...",  ← SHOWN ONCE ONLY!       │    │
│  │      "note": "Save the secret_key now. It will NOT be shown again."│   │
│  │    }                                                               │    │
│  │  }                                                                  │    │
│  │                                                                     │    │
│  │  ⚠️  IMPORTANT: Secret key is hashed (SHA-256) after this response.│    │
│  │      It can NEVER be retrieved again!                              │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER ARCHITECTURE                                │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    CONTROLLER LAYER                                  │   │
│  │                    (REST endpoints)                                  │   │
│  │                                                                      │   │
│  │  • Receives HTTP requests                                           │   │
│  │  • Validates input                                                  │   │
│  │  • Returns ApiResponse wrapped responses                            │   │
│  │  • Does NOT contain business logic                                  │   │
│  └──────────────────────────────┬──────────────────────────────────────┘   │
│                                 │                                           │
│                                 ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    SERVICE LAYER                                     │   │
│  │                    (Business logic)                                  │   │
│  │                                                                      │   │
│  │  • Generates IDs using IdGenerator                                  │   │
│  │  • Generates API keys with SHA-256 hashing                          │   │
│  │  • Throws common-lib exceptions                                     │   │
│  │  • Handles transactions                                             │   │
│  └──────────────────────────────┬──────────────────────────────────────┘   │
│                                 │                                           │
│                                 ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    REPOSITORY LAYER                                  │   │
│  │                    (Data access)                                     │   │
│  │                                                                      │   │
│  │  • Simple JpaRepository interfaces                                  │   │
│  │  • String IDs (not UUID)                                            │   │
│  │  • Only essential query methods                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Simple Design Philosophy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAYFLOW DESIGN CHOICES                                    │
│                                                                              │
│  We use a SIMPLE approach:                                                  │
│  ─────────────────────────                                                  │
│                                                                              │
│  1. NO separate DTOs for simple endpoints                                   │
│     • Controller accepts Merchant entity directly                          │
│     • Less boilerplate code                                                │
│     • Faster development                                                   │
│                                                                              │
│  2. API key generation is a SEPARATE endpoint                              │
│     • POST /v1/merchants/{merchantId}/api-keys                             │
│     • Not auto-generated on merchant creation                              │
│     • Merchant can generate multiple key pairs                             │
│                                                                              │
│  3. Common-lib exceptions instead of custom ones                           │
│     • ResourceNotFoundException                                            │
│     • DuplicateResourceException                                           │
│     • PayflowException                                                     │
│                                                                              │
│  4. SHA-256 for secret key hashing                                         │
│     • Simple and fast                                                      │
│     • Built into Java (no extra dependency)                                │
│     • Good enough for API key validation                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 API Key Generation Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY GENERATION                                        │
│                                                                              │
│  Key Format:                                                                │
│  ───────────                                                                │
│  pk_test_aBcD1234...    (public key, TEST environment)                     │
│  sk_test_xYzW5678...    (secret key, TEST environment)                     │
│  pk_live_aBcD1234...    (public key, LIVE environment)                     │
│  sk_live_xYzW5678...    (secret key, LIVE environment)                     │
│                                                                              │
│  Generation Process:                                                         │
│  ───────────────────                                                        │
│  1. SecureRandom generates random bytes                                     │
│  2. Base64 URL-safe encoding (no padding)                                   │
│  3. Add prefix based on type and environment                                │
│                                                                              │
│  Storage Strategy:                                                          │
│  ─────────────────                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  public_key      → Stored plain text                               │    │
│  │  secret_key_hash → SHA-256 hash of secret key                      │    │
│  │  key_prefix      → First 12 chars (for identification)             │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Why SHA-256 (not bcrypt)?                                                  │
│  ─────────────────────────                                                  │
│  • API keys are already high-entropy (random)                              │
│  • No need for slow hashing (bcrypt is for passwords)                      │
│  • SHA-256 is faster for high-volume API authentication                    │
│  • Secret key is 32+ chars of random data = unfeasible to brute force      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Ensure merchant database is ready
docker exec -it postgres psql -U payflow -d payflow -c "\dt merchant.*"
# Should show: merchants, api_keys tables

# Ensure Merchant Service compiles
cd merchant-service
mvn clean compile
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create MerchantService

**File: `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`**

Let's break this down line by line:

```java
package com.payflow.merchant.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.merchant.model.ApiKey;
import com.payflow.merchant.model.Merchant;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
```

**Import Analysis:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         IMPORT BREAKDOWN                                     │
│                                                                              │
│  From common-lib:                                                           │
│  ────────────────                                                           │
│  • DuplicateResourceException  → NOT used in this file                     │
│  • ResourceNotFoundException   → Thrown when merchant not found            │
│  • IdGenerator                 → Generates 10-char alphanumeric IDs        │
│                                                                              │
│  From merchant-service:                                                     │
│  ─────────────────────                                                      │
│  • ApiKey                      → Entity for storing API keys               │
│  • Merchant                    → Entity for merchant info                  │
│  • ApiKeyRepository            → JPA repository for api_keys table         │
│  • MerchantRepository          → JPA repository for merchants table        │
│                                                                              │
│  From Lombok:                                                               │
│  ────────────                                                               │
│  • @RequiredArgsConstructor    → Auto-generates constructor                │
│  • @Slf4j                      → Provides 'log' logger                     │
│                                                                              │
│  From Java Security:                                                        │
│  ───────────────────                                                        │
│  • MessageDigest               → For SHA-256 hashing                       │
│  • SecureRandom                → Cryptographically secure random           │
│  • Base64                      → URL-safe encoding for keys                │
│  • HexFormat                   → Convert bytes to hex string               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Class Definition:**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
```

**Annotation Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CLASS ANNOTATIONS                                     │
│                                                                              │
│  @Slf4j                                                                      │
│  ───────                                                                    │
│  • Lombok annotation                                                        │
│  • Generates: private static final Logger log = LoggerFactory.getLogger()  │
│  • You can use: log.info(), log.error(), log.debug()                       │
│                                                                              │
│  @Service                                                                    │
│  ────────                                                                   │
│  • Spring stereotype annotation                                             │
│  • Marks this class as a Spring bean                                       │
│  • Semantically indicates "business logic layer"                           │
│                                                                              │
│  @RequiredArgsConstructor                                                   │
│  ────────────────────────                                                   │
│  • Lombok annotation                                                        │
│  • Generates constructor for all 'final' fields                            │
│  • Enables constructor injection (Spring best practice)                    │
│                                                                              │
│  Generated Constructor:                                                     │
│  ─────────────────────                                                      │
│  public MerchantService(                                                    │
│      MerchantRepository merchantRepository,                                 │
│      ApiKeyRepository apiKeyRepository) {                                   │
│      this.merchantRepository = merchantRepository;                          │
│      this.apiKeyRepository = apiKeyRepository;                              │
│  }                                                                          │
│                                                                              │
│  SecureRandom RANDOM:                                                       │
│  ───────────────────                                                        │
│  • Static constant (shared across all instances)                           │
│  • SecureRandom is thread-safe                                             │
│  • Used for cryptographically secure random generation                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Create Merchant Method:**

```java
    @Transactional
    public Merchant createMerchant(Merchant merchant) {
        merchant.setId(IdGenerator.merchantId());
        // Generate webhook secret for HMAC signing
        merchant.setWebhookSecret(generateRandomString(32));
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant created: {} ({})", saved.getId(), saved.getBusinessName());
        return saved;
    }
```

**Method Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     createMerchant() FLOW                                    │
│                                                                              │
│  Input: Merchant object (from controller, no ID yet)                        │
│  Output: Merchant object (with ID, saved to DB)                             │
│                                                                              │
│  Step 1: Generate ID                                                        │
│  ───────────────────                                                        │
│  merchant.setId(IdGenerator.merchantId());                                  │
│  • IdGenerator.merchantId() returns: "mch_XXXXXXXXXX"                      │
│  • 10-character SecureRandom alphanumeric + prefix                         │
│                                                                              │
│  Step 2: Generate Webhook Secret                                            │
│  ──────────────────────────────                                             │
│  merchant.setWebhookSecret(generateRandomString(32));                       │
│  • 32-character random string                                               │
│  • Used for HMAC signing of webhook payloads                               │
│  • Merchant uses this to verify webhooks are from PayFlow                  │
│                                                                              │
│  Step 3: Save to Database                                                   │
│  ────────────────────────                                                   │
│  merchantRepository.save(merchant);                                         │
│  • JPA inserts new row in merchant.merchants table                         │
│  • Returns saved entity with timestamps populated                          │
│                                                                              │
│  Step 4: Log Success                                                        │
│  ───────────────────                                                        │
│  log.info("Merchant created: {} ({})", saved.getId(), ...)                 │
│  • Logs merchant ID and business name                                      │
│  • {} placeholders prevent string concatenation                            │
│                                                                              │
│  @Transactional:                                                            │
│  ──────────────                                                             │
│  • Ensures atomic operation                                                 │
│  • If any part fails, entire operation rolls back                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Get Merchant Methods:**

```java
    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
    }

    public Merchant getMerchantByUserId(String userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "userId", userId));
    }
```

**Method Explanation:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     GETTER METHODS                                           │
│                                                                              │
│  getMerchant(merchantId):                                                   │
│  ────────────────────────                                                   │
│  • findById() returns Optional<Merchant>                                    │
│  • .orElseThrow() unwraps or throws exception                              │
│  • ResourceNotFoundException from common-lib                               │
│  • Message: "Merchant not found with id: mch_XXXXXXXXXX"                   │
│                                                                              │
│  getMerchantByUserId(userId):                                               │
│  ────────────────────────────                                               │
│  • Uses custom repository method                                           │
│  • Finds merchant by the identity user's ID                                │
│  • Same exception handling pattern                                         │
│  • Message: "Merchant not found with userId: usr_XXXXXXXXXX"               │
│                                                                              │
│  Why Both Methods?                                                          │
│  ─────────────────                                                          │
│  • getMerchant: Direct lookup (internal, API calls)                        │
│  • getMerchantByUserId: Find merchant for logged-in user                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Generate API Key Method

```java
    /**
     * Generate API key pair (public + secret).
     * Public key: pk_tst_xxxx or pk_pay_xxxx (shown anytime)
     * Secret key: sk_tst_xxxx or sk_pay_xxxx (shown ONCE, stored as hash)
     */
    @Transactional
    public ApiKeyResult generateApiKey(String merchantId, ApiKey.KeyType keyType) {
        // Verify merchant exists
        getMerchant(merchantId);

        String prefix = keyType == ApiKey.KeyType.TEST ? "test" : "live";
        String publicKey = "pk_" + prefix + "_" + generateRandomString(20);
        String secretKey = "sk_" + prefix + "_" + generateRandomString(32);
        String secretHash = sha256Hash(secretKey);

        ApiKey apiKey = ApiKey.builder()
                .id(IdGenerator.apiKeyId())
                .merchantId(merchantId)
                .keyType(keyType)
                .publicKey(publicKey)
                .secretKeyHash(secretHash)
                .keyPrefix(secretKey.substring(0, 12)) // First 12 chars for identification
                .status(ApiKey.KeyStatus.ACTIVE)
                .build();

        apiKeyRepository.save(apiKey);
        log.info("API key generated for merchant {}: type={}", merchantId, keyType);

        // Return both keys (secret is shown only this once!)
        return new ApiKeyResult(apiKey.getId(), publicKey, secretKey, keyType);
    }
```

**API Key Generation Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  generateApiKey() DETAILED FLOW                              │
│                                                                              │
│  Step 1: Verify Merchant Exists                                             │
│  ──────────────────────────────                                             │
│  getMerchant(merchantId);                                                   │
│  • Throws ResourceNotFoundException if merchant doesn't exist              │
│  • Important security check!                                                │
│                                                                              │
│  Step 2: Determine Prefix                                                   │
│  ────────────────────────                                                   │
│  String prefix = keyType == ApiKey.KeyType.TEST ? "test" : "live";          │
│                                                                              │
│  ┌──────────────┬────────────────────────────────────────────────────┐     │
│  │ KeyType.TEST │ prefix = "test"                                    │     │
│  │ KeyType.LIVE │ prefix = "live"                                    │     │
│  └──────────────┴────────────────────────────────────────────────────┘     │
│                                                                              │
│  Step 3: Generate Keys                                                      │
│  ─────────────────────                                                      │
│  publicKey = "pk_" + prefix + "_" + generateRandomString(20);              │
│  secretKey = "sk_" + prefix + "_" + generateRandomString(32);              │
│                                                                              │
│  Examples:                                                                  │
│  • pk_test_Xa9bYcDe12FgHiJk34Lm                                            │
│  • sk_test_EXAMPLE_DO_NOT_USE_1234567890abcd                                │
│                                                                              │
│  Step 4: Hash Secret Key                                                    │
│  ───────────────────────                                                    │
│  String secretHash = sha256Hash(secretKey);                                 │
│  • SHA-256 produces 64-character hex string                                │
│  • Original secret key is NEVER stored!                                    │
│                                                                              │
│  Step 5: Build ApiKey Entity                                                │
│  ───────────────────────────                                                │
│  ApiKey apiKey = ApiKey.builder()                                           │
│      .id(IdGenerator.apiKeyId())       // "key_XXXXXXXXXX"                 │
│      .merchantId(merchantId)           // Links to merchant                │
│      .keyType(keyType)                 // TEST or LIVE                     │
│      .publicKey(publicKey)             // Stored plain text                │
│      .secretKeyHash(secretHash)        // Stored as SHA-256 hash           │
│      .keyPrefix(secretKey.substring(0, 12))  // "sk_test_Xa9b"            │
│      .status(ApiKey.KeyStatus.ACTIVE)  // Ready to use                     │
│      .build();                                                              │
│                                                                              │
│  Key Prefix Purpose:                                                        │
│  ───────────────────                                                        │
│  • First 12 chars: "sk_test_Xa9b"                                          │
│  • Helps identify which key is being used                                  │
│  • Useful in logs without exposing full secret                             │
│                                                                              │
│  Step 6: Save and Return                                                    │
│  ───────────────────────                                                    │
│  apiKeyRepository.save(apiKey);                                             │
│  return new ApiKeyResult(id, publicKey, secretKey, keyType);               │
│  • Secret key returned ONLY this one time!                                 │
│  • After this, only hash exists in database                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.3: Validate Secret Key Method

```java
    /**
     * Validate a secret key — find merchant by hashing the provided key.
     */
    public String validateSecretKey(String secretKey) {
        String hash = sha256Hash(secretKey);
        ApiKey apiKey = apiKeyRepository.findBySecretKeyHashAndStatus(hash, ApiKey.KeyStatus.ACTIVE)
                .orElse(null);
        if (apiKey == null) return null;
        return apiKey.getMerchantId();
    }
```

**Validation Flow:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  validateSecretKey() FLOW                                    │
│                                                                              │
│  Input: sk_test_EXAMPLE_DO_NOT_USE_1234567890abcd                            │
│  Output: merchantId (or null if invalid)                                    │
│                                                                              │
│  How It Works:                                                              │
│  ─────────────                                                              │
│                                                                              │
│  1. Hash the provided secret key                                            │
│     ┌──────────────────────────────────────┐                                │
│     │ Input:  sk_test_MnOpQrSt56...        │                                │
│     │         ↓ SHA-256                    │                                │
│     │ Output: a1b2c3d4e5f6...              │                                │
│     └──────────────────────────────────────┘                                │
│                                                                              │
│  2. Search database for matching hash                                       │
│     ┌──────────────────────────────────────┐                                │
│     │ SELECT * FROM api_keys               │                                │
│     │ WHERE secret_key_hash = 'a1b2c3...'  │                                │
│     │   AND status = 'ACTIVE'              │                                │
│     └──────────────────────────────────────┘                                │
│                                                                              │
│  3. Return result                                                           │
│     • Found    → return merchantId                                         │
│     • Not found → return null                                              │
│                                                                              │
│  Why Return null (not exception)?                                           │
│  ────────────────────────────────                                           │
│  • This is called during API authentication                                │
│  • Caller decides how to handle invalid key                                │
│  • Avoids exception overhead for normal auth flow                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.4: Helper Methods

```java
    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
```

**Helper Methods Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HELPER METHODS                                            │
│                                                                              │
│  generateRandomString(length):                                              │
│  ─────────────────────────────                                              │
│                                                                              │
│  Step 1: Create byte array                                                  │
│  byte[] bytes = new byte[length];     // e.g., new byte[20]                │
│                                                                              │
│  Step 2: Fill with random bytes                                             │
│  RANDOM.nextBytes(bytes);             // SecureRandom fills array          │
│                                                                              │
│  Step 3: Encode to Base64 URL-safe                                          │
│  Base64.getUrlEncoder()               // URL-safe characters               │
│      .withoutPadding()                // No trailing '=' characters        │
│      .encodeToString(bytes)           // Convert to string                 │
│      .substring(0, length);           // Take first N characters           │
│                                                                              │
│  Why Base64 URL-safe?                                                       │
│  ────────────────────                                                       │
│  • Standard Base64: uses '+' and '/' (URL-unsafe)                          │
│  • URL-safe Base64: uses '-' and '_' instead                               │
│  • Safe to use in URLs, headers, etc.                                      │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  sha256Hash(input):                                                         │
│  ──────────────────                                                         │
│                                                                              │
│  Step 1: Get MessageDigest instance                                         │
│  MessageDigest md = MessageDigest.getInstance("SHA-256");                   │
│                                                                              │
│  Step 2: Hash the input                                                     │
│  byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));           │
│  • Convert string to bytes using UTF-8                                     │
│  • digest() computes SHA-256 hash (32 bytes)                               │
│                                                                              │
│  Step 3: Convert to hex string                                              │
│  return HexFormat.of().formatHex(hash);                                     │
│  • HexFormat is Java 17+ feature                                           │
│  • Converts 32 bytes to 64-character hex string                            │
│                                                                              │
│  Example:                                                                   │
│  Input:  "sk_test_abc123"                                                  │
│  Output: "a1b2c3d4e5f6789...64 hex chars..."                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.5: ApiKeyResult Record

```java
    public record ApiKeyResult(String keyId, String publicKey, String secretKey, ApiKey.KeyType keyType) {}
}
```

**Record Explanation:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JAVA RECORD                                               │
│                                                                              │
│  public record ApiKeyResult(                                                │
│      String keyId,                                                          │
│      String publicKey,                                                      │
│      String secretKey,                                                      │
│      ApiKey.KeyType keyType                                                 │
│  ) {}                                                                       │
│                                                                              │
│  What This Generates:                                                       │
│  ────────────────────                                                       │
│  • Private final fields for each parameter                                 │
│  • Public accessor methods: keyId(), publicKey(), secretKey(), keyType()   │
│  • Constructor with all parameters                                         │
│  • equals(), hashCode(), toString() methods                                │
│                                                                              │
│  Why Use Record (not class)?                                                │
│  ───────────────────────────                                                │
│  • Immutable by design                                                     │
│  • Perfect for simple data carriers                                        │
│  • Less boilerplate than @Data class                                       │
│  • Java 16+ feature                                                        │
│                                                                              │
│  Usage:                                                                     │
│  ──────                                                                     │
│  ApiKeyResult result = new ApiKeyResult("key_abc", "pk_...", "sk_...", TEST)│
│  result.keyId()    // Returns "key_abc"                                    │
│  result.secretKey() // Returns "sk_..."                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.6: Create MerchantController

**File: `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`**

```java
package com.payflow.merchant.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.merchant.model.ApiKey;
import com.payflow.merchant.model.Merchant;
import com.payflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant onboarding and management")
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @Operation(summary = "Register a new merchant")
    public ResponseEntity<ApiResponse<Merchant>> createMerchant(@RequestBody Merchant merchant) {
        Merchant created = merchantService.createMerchant(merchant);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<ApiResponse<Merchant>> getMerchant(@PathVariable String merchantId) {
        Merchant merchant = merchantService.getMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }

    @PostMapping("/{merchantId}/api-keys")
    @Operation(summary = "Generate API key pair (public + secret)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateApiKey(
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "TEST") String keyType) {

        ApiKey.KeyType type = ApiKey.KeyType.valueOf(keyType.toUpperCase());
        MerchantService.ApiKeyResult result = merchantService.generateApiKey(merchantId, type);

        Map<String, Object> response = Map.of(
                "key_id", result.keyId(),
                "key_type", result.keyType().name(),
                "public_key", result.publicKey(),
                "secret_key", result.secretKey(),
                "note", "Save the secret_key now. It will NOT be shown again."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
```

**Controller Breakdown:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  MERCHANT CONTROLLER STRUCTURE                               │
│                                                                              │
│  Class Annotations:                                                         │
│  ──────────────────                                                         │
│  @RestController          → Combines @Controller + @ResponseBody            │
│  @RequestMapping("/v1/merchants")  → Base path for all endpoints           │
│  @RequiredArgsConstructor → Constructor injection for merchantService       │
│  @Tag(name="Merchants")   → OpenAPI/Swagger grouping                        │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════   │
│                                                                              │
│  Endpoint 1: POST /v1/merchants                                             │
│  ──────────────────────────────────                                         │
│  @PostMapping                                                               │
│  @Operation(summary = "Register a new merchant")                            │
│                                                                              │
│  Input:  @RequestBody Merchant merchant                                     │
│  Output: ResponseEntity<ApiResponse<Merchant>>                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Request Body Example:                                               │   │
│  │  {                                                                   │   │
│  │    "userId": "usr_abc123",                                          │   │
│  │    "businessName": "Acme Store",                                    │   │
│  │    "businessType": "RETAIL"                                         │   │
│  │  }                                                                   │   │
│  │                                                                      │   │
│  │  Response (201 Created):                                            │   │
│  │  {                                                                   │   │
│  │    "success": true,                                                 │   │
│  │    "data": {                                                        │   │
│  │      "id": "mch_XYZ789",                                           │   │
│  │      "userId": "usr_abc123",                                        │   │
│  │      "businessName": "Acme Store",                                  │   │
│  │      "webhookSecret": "generated_32_char_string",                   │   │
│  │      ...                                                            │   │
│  │    }                                                                │   │
│  │  }                                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════   │
│                                                                              │
│  Endpoint 2: GET /v1/merchants/{merchantId}                                 │
│  ──────────────────────────────────────────────                             │
│  @GetMapping("/{merchantId}")                                               │
│  @Operation(summary = "Get merchant by ID")                                 │
│                                                                              │
│  Input:  @PathVariable String merchantId                                    │
│  Output: ResponseEntity<ApiResponse<Merchant>>                              │
│                                                                              │
│  Response (200 OK):                                                         │
│  { "success": true, "data": { merchant object } }                          │
│                                                                              │
│  Response (404 Not Found):                                                  │
│  { "success": false, "message": "Merchant not found with id: ..." }        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  API KEY GENERATION ENDPOINT                                 │
│                                                                              │
│  Endpoint 3: POST /v1/merchants/{merchantId}/api-keys                       │
│  ────────────────────────────────────────────────────────                   │
│  @PostMapping("/{merchantId}/api-keys")                                     │
│  @Operation(summary = "Generate API key pair (public + secret)")            │
│                                                                              │
│  Parameters:                                                                │
│  • @PathVariable String merchantId  → Which merchant                        │
│  • @RequestParam(defaultValue = "TEST") String keyType  → TEST or LIVE     │
│                                                                              │
│  Code Flow:                                                                 │
│  ──────────                                                                 │
│  1. Parse keyType: ApiKey.KeyType.valueOf(keyType.toUpperCase())           │
│     • "test" → KeyType.TEST                                                │
│     • "live" → KeyType.LIVE                                                │
│     • Invalid throws IllegalArgumentException                              │
│                                                                              │
│  2. Generate keys: merchantService.generateApiKey(merchantId, type)         │
│     • Returns ApiKeyResult record                                          │
│                                                                              │
│  3. Build response Map:                                                     │
│     Map.of(                                                                 │
│         "key_id", result.keyId(),                                          │
│         "key_type", result.keyType().name(),                               │
│         "public_key", result.publicKey(),                                  │
│         "secret_key", result.secretKey(),                                  │
│         "note", "Save the secret_key now..."                               │
│     )                                                                       │
│                                                                              │
│  Request Example:                                                           │
│  ────────────────                                                           │
│  POST /v1/merchants/mch_abc123/api-keys?keyType=TEST                       │
│                                                                              │
│  Response (201 Created):                                                    │
│  ─────────────────────                                                      │
│  {                                                                          │
│    "success": true,                                                        │
│    "data": {                                                               │
│      "key_id": "key_xyz789",                                              │
│      "key_type": "TEST",                                                  │
│      "public_key": "pk_test_abc123def456...",                             │
│      "secret_key": "sk_test_xyz789uvw012...",                             │
│      "note": "Save the secret_key now. It will NOT be shown again."       │
│    }                                                                       │
│  }                                                                          │
│                                                                              │
│  ⚠️  WARNING: The secret_key is shown ONLY in this response!               │
│      After this, only the hash exists in the database.                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### 5.1 Compile the Service

```powershell
cd merchant-service
mvn clean compile
```

Expected: BUILD SUCCESS

### 5.2 Start the Service

```powershell
# First, ensure infrastructure is running
docker-compose -f docker-compose-infra.yml up -d

# Start Merchant Service
cd merchant-service
mvn spring-boot:run
```

Expected: Started MerchantServiceApplication on port 8082

### 5.3 Test API Endpoints

**Test 1: Create a Merchant**

```powershell
curl -X POST http://localhost:8082/v1/merchants `
  -H "Content-Type: application/json" `
  -d '{
    "userId": "usr_test123",
    "businessName": "Test Store",
    "businessType": "RETAIL"
  }'
```

Expected Response (201 Created):
```json
{
  "success": true,
  "data": {
    "id": "mch_XXXXXXXXXX",
    "userId": "usr_test123",
    "businessName": "Test Store",
    "businessType": "RETAIL",
    "webhookSecret": "XXXXXXXX...",
    "status": "PENDING",
    ...
  }
}
```

**Test 2: Get Merchant**

```powershell
curl http://localhost:8082/v1/merchants/mch_XXXXXXXXXX
```

**Test 3: Generate API Keys**

```powershell
curl -X POST "http://localhost:8082/v1/merchants/mch_XXXXXXXXXX/api-keys?keyType=TEST"
```

Expected Response (201 Created):
```json
{
  "success": true,
  "data": {
    "key_id": "key_XXXXXXXXXX",
    "key_type": "TEST",
    "public_key": "pk_test_...",
    "secret_key": "sk_test_...",
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

### 5.4 Verify Database

```powershell
docker exec -it postgres psql -U payflow -d payflow -c "SELECT id, business_name, status FROM merchant.merchants;"
docker exec -it postgres psql -U payflow -d payflow -c "SELECT id, merchant_id, key_type, key_prefix FROM merchant.api_keys;"
```

---

## 6. File Structure

After completing this part, your merchant-service should have:

```
merchant-service/
├── pom.xml
└── src/main/java/com/payflow/merchant/
    ├── MerchantServiceApplication.java
    ├── controller/
    │   └── MerchantController.java        ← Created this part
    ├── model/
    │   ├── Merchant.java                  ← Created Part 11
    │   └── ApiKey.java                    ← Created Part 11
    ├── repository/
    │   ├── MerchantRepository.java        ← Created Part 11
    │   └── ApiKeyRepository.java          ← Created Part 11
    └── service/
        └── MerchantService.java           ← Created this part
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KEY LEARNINGS                                        │
│                                                                              │
│  1. Simple Architecture                                                     │
│  ──────────────────────                                                     │
│  • No separate DTOs for simple endpoints                                   │
│  • Entity directly in request/response                                     │
│  • Reduces boilerplate code                                                │
│                                                                              │
│  2. API Key Security                                                        │
│  ───────────────────                                                        │
│  • Secret key shown ONCE only                                              │
│  • SHA-256 hash stored in database                                         │
│  • Key prefix stored for identification                                    │
│  • Public key stored in plain text                                         │
│                                                                              │
│  3. Common-lib Integration                                                  │
│  ─────────────────────────                                                  │
│  • ApiResponse wrapper for all responses                                   │
│  • ResourceNotFoundException for 404s                                      │
│  • IdGenerator for consistent ID format                                    │
│                                                                              │
│  4. Transaction Management                                                  │
│  ─────────────────────────                                                  │
│  • @Transactional on write methods                                         │
│  • Ensures atomic operations                                               │
│  • Automatic rollback on exception                                         │
│                                                                              │
│  5. SecureRandom for Cryptography                                           │
│  ────────────────────────────────                                           │
│  • Never use Random for security                                           │
│  • SecureRandom is cryptographically secure                                │
│  • Static instance is thread-safe                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| `ResourceNotFoundException` | Merchant ID doesn't exist | Check database for valid merchant IDs |
| `IllegalArgumentException` on keyType | Invalid keyType value | Use "TEST" or "LIVE" (case-insensitive) |
| `DataIntegrityViolationException` | Duplicate public_key | Extremely rare; regenerate |
| 500 error on createMerchant | Missing required fields | Include userId, businessName, businessType |
| Empty webhook_secret | Not generated | Happens only if createMerchant bypassed |
| Cannot connect to database | PostgreSQL not running | Run docker-compose up |

### Debug Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING CHECKLIST                                 │
│                                                                              │
│  □ Docker PostgreSQL container running?                                     │
│    docker ps | grep postgres                                                │
│                                                                              │
│  □ merchant schema exists?                                                  │
│    docker exec -it postgres psql -U payflow -d payflow -c "\dn"            │
│                                                                              │
│  □ Tables created?                                                          │
│    docker exec -it postgres psql -U payflow -d payflow -c "\dt merchant.*" │
│                                                                              │
│  □ Service started without errors?                                          │
│    Check console for Spring Boot startup messages                          │
│                                                                              │
│  □ Correct port (8082)?                                                     │
│    curl http://localhost:8082/actuator/health                              │
│                                                                              │
│  □ Common-lib dependency resolved?                                          │
│    mvn dependency:tree | grep common-lib                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

### SHA-256 vs bcrypt

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HASHING COMPARISON                                        │
│                                                                              │
│  SHA-256 (used for API keys):                                               │
│  ────────────────────────────                                               │
│  • Fast computation                                                         │
│  • Fixed output size (256 bits)                                            │
│  • No salt needed for high-entropy inputs                                  │
│  • Good for: API keys, file checksums, tokens                              │
│                                                                              │
│  bcrypt (used for passwords):                                               │
│  ────────────────────────────                                               │
│  • Intentionally slow (work factor)                                        │
│  • Built-in salt                                                           │
│  • Resistant to brute force                                                │
│  • Good for: User passwords (low entropy)                                  │
│                                                                              │
│  Why API keys use SHA-256:                                                  │
│  ─────────────────────────                                                  │
│  • API keys are already random (32+ chars)                                 │
│  • Brute force is computationally impossible                               │
│  • Speed matters for high-volume authentication                            │
│  • 2^256 possible combinations = unfeasible attack                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Record vs Class

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JAVA RECORD VS CLASS                                      │
│                                                                              │
│  Record:                                                                    │
│  ───────                                                                    │
│  public record ApiKeyResult(String keyId, String publicKey) {}             │
│  • Immutable                                                               │
│  • Auto-generates: constructor, accessors, equals, hashCode, toString      │
│  • Perfect for data transfer objects                                       │
│                                                                              │
│  Equivalent Class:                                                          │
│  ─────────────────                                                          │
│  public class ApiKeyResult {                                                │
│      private final String keyId;                                           │
│      private final String publicKey;                                       │
│      public ApiKeyResult(String keyId, String publicKey) {...}             │
│      public String keyId() { return keyId; }                               │
│      public String publicKey() { return publicKey; }                       │
│      public boolean equals(Object o) {...}                                 │
│      public int hashCode() {...}                                           │
│      public String toString() {...}                                        │
│  }                                                                          │
│  • 20+ lines of boilerplate for same functionality!                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

In the next part, we'll add Swagger UI testing for the Merchant Service:

1. Access Swagger UI at `http://localhost:8082/swagger-ui.html`
2. Test all three endpoints interactively
3. Explore the generated OpenAPI documentation
4. Understand how `@Operation` and `@Tag` annotations work

**Navigation:**
- [Previous: Part 11 - Merchant Database](./part-11-merchant-database.md)
- [Next: Part 13 - Merchant Swagger Testing](./part-13-merchant-swagger-testing.md)

---

## Quick Reference

### Endpoints Created

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/merchants` | Create new merchant |
| GET | `/v1/merchants/{merchantId}` | Get merchant by ID |
| POST | `/v1/merchants/{merchantId}/api-keys` | Generate API key pair |

### Key Classes

| Class | Purpose |
|-------|---------|
| `MerchantService` | Business logic for merchant operations |
| `MerchantController` | REST API endpoints |
| `ApiKeyResult` | Record for returning generated key pair |

### Important Methods

| Method | Description |
|--------|-------------|
| `createMerchant()` | Creates merchant with generated ID and webhook secret |
| `getMerchant()` | Retrieves merchant by ID or throws 404 |
| `generateApiKey()` | Creates public/secret key pair, stores hash |
| `validateSecretKey()` | Validates secret key by comparing hashes |
