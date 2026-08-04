# Sprint 2, Part 02: API Key Generation

**Duration:** 45 minutes  
**Prerequisites:** Part 01 completed  
**Goal:** Review existing API key generation and add list/revoke functionality

---

## 1. Learning Objectives

By the end of this part, you will:
- Understand how API keys are generated and stored
- Add the `listApiKeys()` method to return all keys for a merchant
- Add the `revokeApiKey()` method for soft-deleting compromised keys
- Add the `findByMerchantId()` repository method

---

## 2. Existing Implementation Review

### 2.1 Current generateApiKey() Method

**File:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

```java
/**
 * Generate API key pair (public + secret).
 * Public key: pk_test_xxxx or pk_live_xxxx (shown anytime)
 * Secret key: sk_test_xxxx or sk_live_xxxx (shown ONCE, stored as hash)
 */
@Transactional
public ApiKeyResult generateApiKey(String merchantId, ApiKey.KeyType keyType) {
    // 1. Verify merchant exists
    getMerchant(merchantId);

    // 2. Generate key pair
    String prefix = keyType == ApiKey.KeyType.TEST ? "test" : "live";
    String publicKey = "pk_" + prefix + "_" + generateRandomString(20);
    String secretKey = "sk_" + prefix + "_" + generateRandomString(32);
    String secretHash = sha256Hash(secretKey);

    // 3. Build and save entity
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

    // 4. Return result (secret shown ONCE!)
    return new ApiKeyResult(apiKey.getId(), publicKey, secretKey, keyType);
}

public record ApiKeyResult(String keyId, String publicKey, String secretKey, ApiKey.KeyType keyType) {}
```

**Key Points:**
- ✅ Secret key is generated with 32 random characters
- ✅ Only the SHA-256 hash is stored in database
- ✅ Secret key is returned ONCE in the response
- ✅ Key prefix (first 12 chars) stored for identification

---

## 3. Add Repository Method

### 3.1 Update ApiKeyRepository

**File:** `merchant-service/src/main/java/com/payflow/merchant/repository/ApiKeyRepository.java`

```java
package com.payflow.merchant.repository;

import com.payflow.merchant.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    // NEW: Find all keys for a merchant (any status)
    List<ApiKey> findByMerchantId(String merchantId);

    // Existing: Find active keys for a merchant
    List<ApiKey> findByMerchantIdAndStatus(String merchantId, ApiKey.KeyStatus status);

    // Existing: Find by hash for validation
    Optional<ApiKey> findBySecretKeyHashAndStatus(String secretKeyHash, ApiKey.KeyStatus status);

    // Existing: Find by public key
    Optional<ApiKey> findByPublicKey(String publicKey);
}
```

**What's New:**
- Added `findByMerchantId()` — returns ALL keys regardless of status

---

## 4. Add Service Methods

### 4.1 Add listApiKeys() Method

**File:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

Add this method to the existing service:

```java
/**
 * List all API keys for a merchant.
 * Note: Secret key is never returned (only stored as hash).
 */
public List<ApiKeyInfo> listApiKeys(String merchantId) {
    // Verify merchant exists
    getMerchant(merchantId);
    
    return apiKeyRepository.findByMerchantId(merchantId).stream()
            .map(key -> new ApiKeyInfo(
                    key.getId(),
                    key.getKeyType(),
                    key.getPublicKey(),
                    key.getKeyPrefix(),
                    key.getStatus(),
                    key.getLastUsedAt(),
                    key.getCreatedAt()
            ))
            .toList();
}

// DTO for list response (safe to return — no secrets)
public record ApiKeyInfo(
        String keyId,
        ApiKey.KeyType keyType,
        String publicKey,
        String keyPrefix,
        ApiKey.KeyStatus status,
        Instant lastUsedAt,
        Instant createdAt
) {}
```

### 4.2 Add revokeApiKey() Method

```java
/**
 * Revoke an API key (soft delete — mark as REVOKED).
 */
@Transactional
public void revokeApiKey(String merchantId, String keyId) {
    // 1. Find the key
    ApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResourceNotFoundException("ApiKey", "id", keyId));
    
    // 2. Verify the key belongs to this merchant (security check!)
    if (!apiKey.getMerchantId().equals(merchantId)) {
        throw new ResourceNotFoundException("ApiKey", "id", keyId);
    }
    
    // 3. Check if already revoked
    if (apiKey.getStatus() == ApiKey.KeyStatus.REVOKED) {
        throw new IllegalStateException("API key is already revoked");
    }
    
    // 4. Soft delete — mark as revoked
    apiKey.setStatus(ApiKey.KeyStatus.REVOKED);
    apiKeyRepository.save(apiKey);
    log.info("API key revoked: {} for merchant {}", keyId, merchantId);
}
```

---

## 5. Why Soft Delete?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HARD DELETE vs SOFT DELETE                                │
│                                                                              │
│  HARD DELETE                              SOFT DELETE (What we use)         │
│  ───────────                              ─────────────────────────         │
│  DELETE FROM api_keys                     UPDATE api_keys                   │
│  WHERE id = 'key_xxx';                    SET status = 'REVOKED'            │
│                                           WHERE id = 'key_xxx';             │
│                                                                              │
│  Problems with Hard Delete:               Benefits of Soft Delete:          │
│  ─────────────────────────                ────────────────────────          │
│  • Lost audit trail                       • Full audit trail                │
│  • Can't tell if key existed             • Know when key was revoked       │
│  • Possible key_id collision              • Key_id never reused             │
│  • No forensics for breach               • Forensics for security team     │
│                                                                              │
│  Security Scenario:                                                          │
│  ──────────────────                                                         │
│  Merchant's key is leaked. Attacker tries to use it.                       │
│                                                                              │
│  Hard delete: "Invalid key" (was it ever valid? Unknown)                   │
│  Soft delete: "Key revoked at 2024-01-15 10:30:00" → full audit            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Add Controller Endpoints

### 6.1 Update MerchantController

**File:** `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`

Add these endpoints:

```java
// ==================== API KEY ENDPOINTS ====================

@GetMapping("/{merchantId}/api-keys")
@Operation(summary = "List all API keys for a merchant")
public ResponseEntity<ApiResponse<List<MerchantService.ApiKeyInfo>>> listApiKeys(
        @PathVariable String merchantId) {
    List<MerchantService.ApiKeyInfo> keys = merchantService.listApiKeys(merchantId);
    return ResponseEntity.ok(ApiResponse.success(keys));
}

@DeleteMapping("/{merchantId}/api-keys/{keyId}")
@Operation(summary = "Revoke an API key")
public ResponseEntity<ApiResponse<Map<String, String>>> revokeApiKey(
        @PathVariable String merchantId,
        @PathVariable String keyId) {
    merchantService.revokeApiKey(merchantId, keyId);
    return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", "API key revoked successfully",
            "key_id", keyId
    )));
}
```

---

## 7. Complete Updated Files

### 7.1 Complete MerchantService.java

**File:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

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
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== MERCHANT METHODS ====================

    @Transactional
    public Merchant createMerchant(Merchant merchant) {
        merchant.setId(IdGenerator.merchantId());
        merchant.setWebhookSecret(generateRandomString(32));
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant created: {} ({})", saved.getId(), saved.getBusinessName());
        return saved;
    }

    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
    }

    public Merchant getMerchantByUserId(String userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "userId", userId));
    }

    // ==================== API KEY METHODS ====================

    @Transactional
    public ApiKeyResult generateApiKey(String merchantId, ApiKey.KeyType keyType) {
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
                .keyPrefix(secretKey.substring(0, 12))
                .status(ApiKey.KeyStatus.ACTIVE)
                .build();

        apiKeyRepository.save(apiKey);
        log.info("API key generated for merchant {}: type={}", merchantId, keyType);

        return new ApiKeyResult(apiKey.getId(), publicKey, secretKey, keyType);
    }

    public String validateSecretKey(String secretKey) {
        String hash = sha256Hash(secretKey);
        ApiKey apiKey = apiKeyRepository.findBySecretKeyHashAndStatus(hash, ApiKey.KeyStatus.ACTIVE)
                .orElse(null);
        if (apiKey == null) return null;
        return apiKey.getMerchantId();
    }

    public List<ApiKeyInfo> listApiKeys(String merchantId) {
        getMerchant(merchantId);
        
        return apiKeyRepository.findByMerchantId(merchantId).stream()
                .map(key -> new ApiKeyInfo(
                        key.getId(),
                        key.getKeyType(),
                        key.getPublicKey(),
                        key.getKeyPrefix(),
                        key.getStatus(),
                        key.getLastUsedAt(),
                        key.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void revokeApiKey(String merchantId, String keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", "id", keyId));
        
        if (!apiKey.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("ApiKey", "id", keyId);
        }
        
        if (apiKey.getStatus() == ApiKey.KeyStatus.REVOKED) {
            throw new IllegalStateException("API key is already revoked");
        }
        
        apiKey.setStatus(ApiKey.KeyStatus.REVOKED);
        apiKeyRepository.save(apiKey);
        log.info("API key revoked: {} for merchant {}", keyId, merchantId);
    }

    // ==================== UTILITY METHODS ====================

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

    // ==================== DTOs ====================

    public record ApiKeyResult(String keyId, String publicKey, String secretKey, ApiKey.KeyType keyType) {}

    public record ApiKeyInfo(
            String keyId,
            ApiKey.KeyType keyType,
            String publicKey,
            String keyPrefix,
            ApiKey.KeyStatus status,
            Instant lastUsedAt,
            Instant createdAt
    ) {}
}
```

---

## 8. Testing

### 8.1 Generate API Keys

```powershell
# Create a merchant first (if not exists)
curl -X POST http://localhost:8082/v1/merchants `
  -H "Content-Type: application/json" `
  -d '{"userId":"usr_test123","businessName":"Test Shop","businessType":"INDIVIDUAL"}'

# Generate TEST key
curl -X POST "http://localhost:8082/v1/merchants/merch_xxxxx/api-keys?keyType=TEST"

# Generate LIVE key
curl -X POST "http://localhost:8082/v1/merchants/merch_xxxxx/api-keys?keyType=LIVE"
```

### 8.2 List API Keys

```powershell
curl http://localhost:8082/v1/merchants/merch_xxxxx/api-keys
```

**Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "keyId": "key_abc123",
      "keyType": "TEST",
      "publicKey": "pk_test_5G8nK2mPq9vX3hJ7LkYw",
      "keyPrefix": "sk_test_abc1",
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:30:00Z"
    },
    {
      "keyId": "key_def456",
      "keyType": "LIVE",
      "publicKey": "pk_live_9X2mN7pQk3vH5jL8RtUi",
      "keyPrefix": "sk_live_def4",
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:31:00Z"
    }
  ]
}
```

### 8.3 Revoke API Key

```powershell
curl -X DELETE http://localhost:8082/v1/merchants/merch_xxxxx/api-keys/key_abc123
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "message": "API key revoked successfully",
    "key_id": "key_abc123"
  }
}
```

---

## 9. API Reference

### 9.1 Generate API Key

```
POST /v1/merchants/{merchantId}/api-keys?keyType=TEST|LIVE

Response (201 Created):
{
  "success": true,
  "data": {
    "key_id": "key_xxx",
    "key_type": "TEST",
    "public_key": "pk_test_xxx",
    "secret_key": "sk_test_xxx",  ← SHOWN ONCE!
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

### 9.2 List API Keys

```
GET /v1/merchants/{merchantId}/api-keys

Response (200 OK):
{
  "success": true,
  "data": [
    {
      "keyId": "key_xxx",
      "keyType": "TEST",
      "publicKey": "pk_test_xxx",
      "keyPrefix": "sk_test_xxx1",  ← Only prefix, not full secret
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### 9.3 Revoke API Key

```
DELETE /v1/merchants/{merchantId}/api-keys/{keyId}

Response (200 OK):
{
  "success": true,
  "data": {
    "message": "API key revoked successfully",
    "key_id": "key_xxx"
  }
}

Response (400 Bad Request) - Already revoked:
{
  "success": false,
  "error": {
    "code": "BAD_REQUEST",
    "message": "API key is already revoked"
  }
}
```

---

## 10. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Secret shown once** | Only returned at generation time |
| **Soft delete** | Mark as REVOKED, never hard delete |
| **Ownership check** | Always verify merchantId before operations |
| **Key prefix** | First 12 chars for identification |
| **DTO pattern** | ApiKeyInfo excludes secret hash |

---

## 11. Next Steps

**Continue to:** [part-03-api-key-authentication.md](./part-03-api-key-authentication.md)

In the next part, you'll create the API Key Authentication Filter in the gateway.

---

**End of Sprint 2, Part 02**
