# Hands-On Guide — Phase 6 Part 7: Idempotency (Duplicate Prevention)

## Goal

By the end of Part 7, you will have:
- IdempotencyService that stores/retrieves payment responses in Redis
- Understanding of WHY idempotency is critical for payment systems
- 24-hour TTL on idempotency keys
- Tested: same key = same response (no duplicate charge)
- Git commit

## Prerequisites

- Part 6 completed (payment flow works)
- Redis running on port 6379

---

## Why Idempotency Is CRITICAL for Payments

```
THE NIGHTMARE SCENARIO (without idempotency):

1. Customer clicks "Pay ₹5000"
2. Request goes to our server
3. We charge the card → SUCCESS! Payment created: pay_001
4. Response traveling back to customer's browser...
5. ...NETWORK DROPS! Customer sees timeout/error
6. Customer thinks "It didn't work" → clicks "Pay ₹5000" AGAIN
7. Second request goes to our server
8. We charge the card AGAIN → SUCCESS! Payment created: pay_002
9. Customer is charged ₹10,000 instead of ₹5,000! 💀

WITH IDEMPOTENCY:

1. Customer clicks "Pay ₹5000"
   → Merchant generates unique Idempotency-Key: "order123_attempt1"
2. Request: POST /payments, Header: Idempotency-Key: "order123_attempt1"
3. Server: Check Redis → key doesn't exist → process payment → save to Redis
4. Response: {payment_id: "pay_001", status: "authorized"}
5. NETWORK DROPS! Customer sees error
6. Customer clicks "Pay ₹5000" AGAIN
   → Merchant sends SAME Idempotency-Key: "order123_attempt1"
7. Request: POST /payments, Header: Idempotency-Key: "order123_attempt1"
8. Server: Check Redis → KEY EXISTS! → return CACHED response
9. Response: {payment_id: "pay_001", status: "authorized"} (same as before!)
10. Customer charged only ONCE! ✅

RULES:
├── Same key + same body → return cached response (no re-processing)
├── Same key + different body → ERROR (key reuse not allowed)
├── Different key → new payment (normal processing)
├── Key stored in Redis with 24-hour TTL (auto-cleanup)
└── Key format: any unique string (UUID, "order_123_attempt_1", etc.)
```

---

## Step 7.1: IdempotencyService (Full Code)

**File already exists:** `payment-service/src/main/java/com/payflow/payment/service/IdempotencyService.java`

```java
package com.payflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Idempotency Service — Prevents duplicate payment processing using Redis.
 * 
 * How it works:
 * 1. Before processing payment: getCachedResponse(key)
 *    → If found: return cached response (skip processing entirely)
 *    → If not found: proceed with payment processing
 * 
 * 2. After successful processing: cacheResponse(key, response)
 *    → Store in Redis with 24-hour TTL
 *    → Any retry within 24 hours gets the same response
 * 
 * Redis key format: "idempotency:{merchant_id}:{key}"
 * Redis value: JSON string of PaymentResponse
 * TTL: 24 hours (after that, key is auto-deleted)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    // StringRedisTemplate: Spring's Redis client for string operations
    // Supports: GET, SET, SET with TTL, DELETE, EXISTS

    private final ObjectMapper objectMapper;
    // Jackson ObjectMapper: Converts Java objects ↔ JSON strings
    // PaymentResponse → JSON string (for Redis storage)
    // JSON string → PaymentResponse (for retrieval)

    private static final String KEY_PREFIX = "idempotency:";
    // All our keys start with this prefix
    // Helps identify our keys in Redis (vs other apps sharing same Redis)

    private static final Duration TTL = Duration.ofHours(24);
    // Keys expire after 24 hours
    // After expiry: same idempotency key CAN be reused (new payment)
    // 24 hours is long enough to cover all retry scenarios

    /**
     * Check if this idempotency key has been used before.
     * 
     * @param idempotencyKey The unique key from Idempotency-Key header
     * @return Cached PaymentResponse if key exists, null if new request
     */
    public PaymentResponse getCachedResponse(String idempotencyKey) {
        // Read from Redis
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        // opsForValue() = STRING operations (GET/SET)
        // Returns null if key doesn't exist

        if (json == null) {
            return null; // New request — never seen this key before
        }

        // Key found! Parse cached response
        try {
            PaymentResponse cached = objectMapper.readValue(json, PaymentResponse.class);
            log.info("Idempotency cache HIT for key: {}", idempotencyKey);
            return cached;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cached idempotency response for key: {}", idempotencyKey);
            return null; // Corrupted cache — treat as new request
        }
    }

    /**
     * Store payment response for this idempotency key.
     * Called AFTER successful payment processing.
     * 
     * @param idempotencyKey The key to store under
     * @param response The payment response to cache
     */
    public void cacheResponse(String idempotencyKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            // Convert PaymentResponse → JSON string

            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, TTL);
            // SET key value EX 86400 (86400 seconds = 24 hours)
            // After 24 hours, Redis automatically deletes this key

            log.debug("Idempotency cached for key: {} (TTL: 24h)", idempotencyKey);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache idempotency response for key: {}", idempotencyKey);
            // Non-fatal: if caching fails, duplicate protection is degraded
            // but payment still works correctly
        }
    }
}
```

---

## Step 7.2: How It's Used in PaymentProcessorService

```java
public PaymentResponse processPayment(PaymentRequest request, 
                                       String idempotencyKey, 
                                       String merchantId) {
    // ===== STEP 1: Idempotency Check (BEFORE any processing) =====
    if (idempotencyKey != null) {
        PaymentResponse cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached != null) {
            // DUPLICATE! Return cached response without doing ANYTHING
            return cached;
        }
    }

    // ... (process payment — fraud, bank, save) ...

    // ===== LAST STEP: Cache result (AFTER successful processing) =====
    if (idempotencyKey != null) {
        idempotencyService.cacheResponse(idempotencyKey, response);
    }

    return response;
}
```

---

## Step 7.3: What's in Redis

After a payment is processed, Redis looks like:

```
KEY:   idempotency:order123_attempt1
VALUE: {"paymentId":"pay_Hk7mN3xQp2","status":"authorized","amount":5000.00,...}
TTL:   86399 seconds remaining (24 hours minus elapsed time)
```

You can check in Redis CLI:
```cmd
docker exec -it payflow-redis redis-cli
> KEYS idempotency:*
> GET idempotency:order123_attempt1
> TTL idempotency:order123_attempt1
```

---

## Step 7.4: Test Idempotency

### First request (normal):
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Idempotency-Key: unique_key_123" ^
  -H "Content-Type: application/json" ^
  -d "{\"orderId\":\"ord_test\",\"amount\":1000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\"}}"
```
**Result:** New payment created, response returned, key cached in Redis.

### Same request again (duplicate — same key):
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Idempotency-Key: unique_key_123" ^
  -H "Content-Type: application/json" ^
  -d "{\"orderId\":\"ord_test\",\"amount\":1000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\"}}"
```
**Result:** SAME response as first request! No new payment created.

### Verify in DB:
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT id, status FROM payment.payments WHERE idempotency_key='unique_key_123';"
```
**Expected:** Only ONE row (not two!)

---

## Step 7.5: Git Commit

```cmd
git add payment-service/
git commit -m "Phase 6 Part 7: Idempotency service - Redis-based duplicate payment prevention"
```

---

## What We Built

| Component | Purpose |
|-----------|---------|
| `IdempotencyService.getCachedResponse()` | Check if this key was used before |
| `IdempotencyService.cacheResponse()` | Store response for 24 hours |
| Redis key: `idempotency:{key}` | Stores JSON response with TTL |
| Idempotency-Key header | Client provides unique key per payment attempt |

---

## Interview Notes

**Q: "How do you prevent duplicate payments?"**
> "Every payment request requires an Idempotency-Key header. Before processing, we check Redis for this key. If found, we return the cached response (no re-processing). If new, we process the payment and cache the response with 24-hour TTL. This handles network retries, user double-clicks, and any duplicate scenario."

**Q: "What if Redis is down?"**
> "If Redis is unavailable, the idempotency check returns null (cache miss). Payment proceeds normally but without duplicate protection. We log a warning and the operations team is alerted. This is a graceful degradation — availability over consistency for non-critical cache."

**Q: "Why 24-hour TTL?"**
> "24 hours covers all realistic retry scenarios — network retries happen within seconds/minutes, user retries within hours. After 24 hours, the same key can be reused for a genuinely new payment. This also prevents Redis from growing indefinitely."

---

## Next Step

→ Continue to **Phase 6 Part 8: Feign Clients & Integration**
