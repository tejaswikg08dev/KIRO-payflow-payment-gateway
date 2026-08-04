# Hands-On Guide — Phase 5 Part 2: Merchant Onboarding

## Goal

By the end of Part 2, you will have:
- MerchantService with createMerchant() and getMerchant() logic
- Webhook secret auto-generated on registration
- MerchantController with POST /v1/merchants and GET /v1/merchants/{id}
- Working registration tested with curl
- Git commit

## Prerequisites

- Part 1 completed (merchant-service starts, tables exist)
- Docker running

---

## How Merchant Onboarding Works

```
STEP 1: User registers via identity-service (gets user_id + JWT token)
STEP 2: User creates merchant profile via merchant-service:
         POST /v1/merchants
         Headers: Authorization: Bearer {jwt_token}
         Body: { businessName, businessType, websiteUrl, bankAccount, ... }
STEP 3: System:
         a. Generate merchant ID: merch_Hk7mN3xQp2
         b. Generate webhook secret: 32-char random (for HMAC signing later)
         c. Set status = PENDING (awaiting KYC verification)
         d. Save to database
         e. Return merchant details
STEP 4: Admin verifies KYC → sets status = ACTIVE
STEP 5: Merchant can now generate API keys and accept payments
```

---

## Step 2.1: Create MerchantService

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

```java
package com.payflow.merchant.service;

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

@Slf4j
// Creates a logger: log.info("message"), log.error("error"), etc.

@Service
// Spring manages this as a singleton bean

@RequiredArgsConstructor
// Lombok generates constructor: MerchantService(MerchantRepository repo, ApiKeyRepository keyRepo)
// Spring auto-injects both repositories via constructor injection
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
    // SecureRandom: Cryptographically secure random number generator
    // Better than Math.random() for security-sensitive operations (keys, tokens)

    /**
     * Create a new merchant profile.
     * Called when a user completes the merchant registration form.
     */
    @Transactional
    // @Transactional: If anything fails, the entire operation is rolled back
    // Either everything saves successfully, or nothing does (atomicity)
    public Merchant createMerchant(Merchant merchant) {
        // 1. Generate unique merchant ID
        merchant.setId(IdGenerator.merchantId());
        // Result: "merch_Hk7mN3xQp2" (prefix + 10 random chars)

        // 2. Generate webhook secret (used to sign webhook payloads with HMAC)
        merchant.setWebhookSecret(generateRandomString(32));
        // Result: "aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1v" (32 chars)
        // Merchant saves this to verify incoming webhooks are from us

        // 3. Save to database
        Merchant saved = merchantRepository.save(merchant);

        log.info("Merchant created: {} ({})", saved.getId(), saved.getBusinessName());
        return saved;
    }

    /**
     * Get merchant by ID.
     * Throws 404 if not found.
     */
    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
        // If not found: returns {"error": {"code": "MERCHANT_NOT_FOUND", "message": "..."}}
    }

    /**
     * Get merchant by user ID (from identity-service).
     * Used when user is logged in and wants to see their merchant profile.
     */
    public Merchant getMerchantByUserId(String userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "userId", userId));
    }

    /**
     * Generate API key pair for a merchant.
     *
     * Returns:
     *   publicKey:  pk_pay_51a2b3c4d5e6f7g8h9  (safe to show anytime)
     *   secretKey:  sk_pay_xYz123AbC456dEf789... (shown ONCE, then only hash stored)
     *
     * How authentication works later:
     *   1. Merchant sends: X-Api-Key: sk_pay_xYz123AbC456dEf789...
     *   2. We compute: SHA-256(sk_pay_xYz123AbC456dEf789...) = "abc123hash..."
     *   3. We look up: SELECT * FROM api_keys WHERE secret_key_hash = 'abc123hash...'
     *   4. Found + ACTIVE → merchant authenticated!
     */
    @Transactional
    public ApiKeyResult generateApiKey(String merchantId, ApiKey.KeyType keyType) {
        // Verify merchant exists
        getMerchant(merchantId);

        // Determine prefix based on key type
        String prefix = keyType == ApiKey.KeyType.TEST ? "test" : "live";

        // Generate key pair
        String publicKey = "pk_" + prefix + "_" + generateRandomString(20);
        // Example: "pk_pay_aB3dE5fG7hI9jK1lM3nO"

        String secretKey = "sk_" + prefix + "_" + generateRandomString(32);
        // Example: "sk_pay_xYz123AbC456dEf789GhI012JkL345mNo"

        // Hash the secret (we store ONLY the hash, never the actual secret)
        String secretHash = sha256Hash(secretKey);
        // Result: "a3f2b1c4d5e6f7..." (64 hex chars — SHA-256 output)

        // Create entity
        ApiKey apiKey = ApiKey.builder()
                .id(IdGenerator.apiKeyId())
                .merchantId(merchantId)
                .keyType(keyType)
                .publicKey(publicKey)
                .secretKeyHash(secretHash)
                .keyPrefix(secretKey.substring(0, 12))
                // First 12 chars: "sk_pay_xYz1" (for log identification)
                .status(ApiKey.KeyStatus.ACTIVE)
                .build();

        apiKeyRepository.save(apiKey);
        log.info("API key generated for merchant {}: type={}, prefix={}", 
                merchantId, keyType, apiKey.getKeyPrefix());

        // Return both keys (secret shown ONLY this once!)
        return new ApiKeyResult(apiKey.getId(), publicKey, secretKey, keyType);
    }

    /**
     * Validate an incoming API secret key.
     * Called by API Gateway or internal filter on every merchant API request.
     *
     * @param secretKey The raw secret key from X-Api-Key header
     * @return merchant ID if valid, null if invalid/revoked
     */
    public String validateSecretKey(String secretKey) {
        // Hash the incoming key
        String hash = sha256Hash(secretKey);

        // Look up in database
        ApiKey apiKey = apiKeyRepository
                .findBySecretKeyHashAndStatus(hash, ApiKey.KeyStatus.ACTIVE)
                .orElse(null);

        if (apiKey == null) return null; // Invalid or revoked key
        return apiKey.getMerchantId(); // Valid! Return the merchant ID
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Generate a cryptographically secure random string.
     * Used for webhook secrets and API keys.
     */
    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        // SecureRandom fills array with cryptographically random bytes
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                .substring(0, length);
        // Base64 URL-safe encoding: A-Z, a-z, 0-9, -, _
        // Trim to exact length requested
    }

    /**
     * Compute SHA-256 hash of a string.
     * Used to hash API secret keys before storing in database.
     *
     * SHA-256 properties:
     * - One-way: cannot reverse hash → original
     * - Deterministic: same input always gives same hash
     * - Fixed length: always 64 hex characters (256 bits)
     * - Avalanche: tiny change in input → completely different hash
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
            // Convert byte[] to hex string: [0xAB, 0xCD] → "abcd"
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
            // This should never happen (SHA-256 is always available in Java)
        }
    }

    /**
     * Result record for API key generation.
     * Record = immutable data class (Java 16+)
     */
    public record ApiKeyResult(
            String keyId,
            String publicKey,
            String secretKey,  // Shown once!
            ApiKey.KeyType keyType
    ) {}
}
```

---

## Step 2.2: Create MerchantController

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`

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
    @Operation(summary = "Register a new merchant",
            description = "Creates merchant profile. Status starts as PENDING until KYC verified.")
    public ResponseEntity<ApiResponse<Merchant>> createMerchant(
            @RequestBody Merchant merchant) {
        Merchant created = merchantService.createMerchant(merchant);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created));
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<ApiResponse<Merchant>> getMerchant(
            @PathVariable String merchantId) {
        Merchant merchant = merchantService.getMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }

    @PostMapping("/{merchantId}/api-keys")
    @Operation(summary = "Generate API key pair (public + secret)",
            description = "Secret key is shown ONCE in response. Store it securely!")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateApiKey(
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "TEST") String keyType) {

        ApiKey.KeyType type = ApiKey.KeyType.valueOf(keyType.toUpperCase());
        MerchantService.ApiKeyResult result = merchantService.generateApiKey(merchantId, type);

        // Build response with clear field names
        Map<String, Object> response = Map.of(
                "key_id", result.keyId(),
                "key_type", result.keyType().name(),
                "public_key", result.publicKey(),
                "secret_key", result.secretKey(),
                "note", "⚠️ Save the secret_key now. It will NOT be shown again."
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
```

---

## Step 2.3: Verify with curl

### Create a merchant:
```cmd
curl -X POST http://localhost:8082/v1/merchants ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\":\"usr_test123\",\"businessName\":\"TechShop India\",\"businessType\":\"COMPANY\",\"websiteUrl\":\"https://techshop.in\",\"webhookUrl\":\"https://techshop.in/webhooks\"}"
```

**Expected (201):**
```json
{
  "success": true,
  "data": {
    "id": "merch_aB3dE5fG7h",
    "userId": "usr_test123",
    "businessName": "TechShop India",
    "businessType": "COMPANY",
    "webhookSecret": "xYz123AbC456...",
    "status": "PENDING",
    "mdrPercentage": 2.00,
    "settlementSchedule": "T+2"
  }
}
```

### Generate API key:
```cmd
curl -X POST "http://localhost:8082/v1/merchants/merch_aB3dE5fG7h/api-keys?keyType=LIVE"
```

**Expected (201):**
```json
{
  "success": true,
  "data": {
    "key_id": "key_Mn2kP9wQr5",
    "key_type": "LIVE",
    "public_key": "pk_pay_51a2b3c4d5e6f7g8h9",
    "secret_key": "sk_pay_xYz123AbC456dEf789GhI012JkL345mNo",
    "note": "⚠️ Save the secret_key now. It will NOT be shown again."
  }
}
```

---

## Step 2.4: Git Commit

```cmd
git add merchant-service/src/main/java/com/payflow/merchant/service/
git add merchant-service/src/main/java/com/payflow/merchant/controller/
git commit -m "Phase 5 Part 2: MerchantService + MerchantController (onboarding + API key generation)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `service/MerchantService.java` | Create merchant, generate API keys, validate keys, SHA-256 hashing |
| `controller/MerchantController.java` | POST /merchants, GET /merchants/{id}, POST /api-keys |

---

## Next Step

→ Continue to **Phase 5 Part 3: API Key Management**

In Part 3, we'll add:
- API key validation logic (authenticate merchant from X-Api-Key header)
- Key rotation (revoke old, generate new)
- List active keys for a merchant
