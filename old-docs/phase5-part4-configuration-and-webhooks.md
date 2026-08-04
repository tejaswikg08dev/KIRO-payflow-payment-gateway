# Hands-On Guide — Phase 5 Part 4: Configuration & Webhooks

## Goal

By the end of Part 4, you will have:
- Update webhook URL endpoint (PUT /v1/merchants/{id}/webhook)
- Update settlement schedule endpoint
- Update fee configuration endpoint
- Understanding of webhook secret rotation
- Git commit

## Prerequisites

- Part 3 completed (API key management working)

---

## How Webhook Configuration Works

```
MERCHANT SETUP:
1. During onboarding, merchant provides webhook_url:
   "https://myshop.com/webhooks/payflow"

2. We generate a webhook_secret for them:
   "aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1v"

3. When payment events happen, our webhook-service:
   a. Takes the event JSON payload
   b. Signs it: HMAC-SHA256(webhook_secret, timestamp + "." + payload)
   c. POSTs to merchant's webhook_url with signature in header

4. Merchant verifies:
   a. Gets signature from X-PayFlow-Signature header
   b. Computes HMAC-SHA256 with their stored secret
   c. Compares → match means it's really from PayFlow

WEBHOOK URL UPDATE:
- Merchant may change their server URL (new domain, new endpoint)
- PUT /v1/merchants/{id}/webhook → updates webhook_url

WEBHOOK SECRET ROTATION:
- If merchant's secret is compromised, they need a new one
- POST /v1/merchants/{id}/webhook/rotate-secret → generates new 32-char secret
- Old webhooks signed with old secret will fail verification
- Merchant updates their verification code with new secret
```

---

## Step 4.1: Create DTOs for Update Operations

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/dto/UpdateWebhookRequest.java`

```java
package com.payflow.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWebhookRequest {

    @NotBlank(message = "Webhook URL is required")
    @URL(message = "Must be a valid URL (https://...)")
    private String webhookUrl;
    // Example: "https://myshop.com/webhooks/payflow"
    // Must be HTTPS in production (we accept HTTP for local testing)
}
```

**Create file:** `merchant-service/src/main/java/com/payflow/merchant/dto/UpdateFeeRequest.java`

```java
package com.payflow.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFeeRequest {

    @DecimalMin(value = "0.00", message = "MDR percentage cannot be negative")
    @DecimalMax(value = "10.00", message = "MDR percentage cannot exceed 10%")
    private BigDecimal mdrPercentage;
    // Example: 2.00 means 2% of each transaction is our fee

    private String settlementSchedule;
    // T+1, T+2, T+3, WEEKLY
}
```

---

## Step 4.2: Add Update Methods to MerchantService

**Add to:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

```java
    /**
     * Update merchant's webhook URL.
     * Called when merchant changes their server endpoint.
     */
    @Transactional
    public Merchant updateWebhookUrl(String merchantId, String webhookUrl) {
        Merchant merchant = getMerchant(merchantId);
        merchant.setWebhookUrl(webhookUrl);
        Merchant saved = merchantRepository.save(merchant);
        log.info("Webhook URL updated for merchant {}: {}", merchantId, webhookUrl);
        return saved;
    }

    /**
     * Rotate webhook secret (generate new one).
     * Old webhooks will fail verification with new secret.
     * Merchant must update their verification code immediately.
     */
    @Transactional
    public String rotateWebhookSecret(String merchantId) {
        Merchant merchant = getMerchant(merchantId);
        String newSecret = generateRandomString(32);
        merchant.setWebhookSecret(newSecret);
        merchantRepository.save(merchant);
        log.info("Webhook secret rotated for merchant {}", merchantId);
        return newSecret;
        // This is shown ONCE — merchant must save it
    }

    /**
     * Update fee configuration.
     */
    @Transactional
    public Merchant updateFeeConfig(String merchantId, BigDecimal mdrPercentage, String settlementSchedule) {
        Merchant merchant = getMerchant(merchantId);
        if (mdrPercentage != null) {
            merchant.setMdrPercentage(mdrPercentage);
        }
        if (settlementSchedule != null) {
            // Validate schedule format
            if (!List.of("T+1", "T+2", "T+3", "WEEKLY").contains(settlementSchedule)) {
                throw new PayflowException("INVALID_SCHEDULE",
                        "Settlement schedule must be T+1, T+2, T+3, or WEEKLY",
                        HttpStatus.BAD_REQUEST);
            }
            merchant.setSettlementSchedule(settlementSchedule);
        }
        Merchant saved = merchantRepository.save(merchant);
        log.info("Fee config updated for merchant {}: MDR={}%, schedule={}",
                merchantId, saved.getMdrPercentage(), saved.getSettlementSchedule());
        return saved;
    }
```

---

## Step 4.3: Add Endpoints to MerchantController

**Add to:** `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`

```java
    @PutMapping("/{merchantId}/webhook")
    @Operation(summary = "Update webhook URL",
            description = "Changes where we deliver webhook events for this merchant")
    public ResponseEntity<ApiResponse<Merchant>> updateWebhookUrl(
            @PathVariable String merchantId,
            @Valid @RequestBody UpdateWebhookRequest request) {
        Merchant updated = merchantService.updateWebhookUrl(merchantId, request.getWebhookUrl());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{merchantId}/webhook/rotate-secret")
    @Operation(summary = "Rotate webhook secret",
            description = "Generates a new webhook secret. Old secret stops working immediately. "
                + "Save the new secret — it's shown only once!")
    public ResponseEntity<ApiResponse<Map<String, String>>> rotateWebhookSecret(
            @PathVariable String merchantId) {
        String newSecret = merchantService.rotateWebhookSecret(merchantId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "webhook_secret", newSecret,
                "note", "Save this secret now. It will NOT be shown again."
        )));
    }

    @PutMapping("/{merchantId}/fees")
    @Operation(summary = "Update fee configuration",
            description = "Change MDR percentage and/or settlement schedule")
    public ResponseEntity<ApiResponse<Merchant>> updateFeeConfig(
            @PathVariable String merchantId,
            @Valid @RequestBody UpdateFeeRequest request) {
        Merchant updated = merchantService.updateFeeConfig(
                merchantId, request.getMdrPercentage(), request.getSettlementSchedule());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
```

**Add imports:**
```java
import com.payflow.merchant.dto.UpdateWebhookRequest;
import com.payflow.merchant.dto.UpdateFeeRequest;
import jakarta.validation.Valid;
import java.util.Map;
```

---

## Step 4.4: Verify with curl

### Update webhook URL:
```cmd
curl -X PUT http://localhost:8082/v1/merchants/merch_aB3dE5fG7h/webhook ^
  -H "Content-Type: application/json" ^
  -d "{\"webhookUrl\":\"https://newserver.techshop.in/webhooks\"}"
```

### Rotate webhook secret:
```cmd
curl -X POST http://localhost:8082/v1/merchants/merch_aB3dE5fG7h/webhook/rotate-secret
```

**Expected:** New 32-char secret returned.

### Update fees:
```cmd
curl -X PUT http://localhost:8082/v1/merchants/merch_aB3dE5fG7h/fees ^
  -H "Content-Type: application/json" ^
  -d "{\"mdrPercentage\":1.80,\"settlementSchedule\":\"T+1\"}"
```

---

## Step 4.5: Git Commit

```cmd
git add merchant-service/
git commit -m "Phase 5 Part 4: Webhook config, secret rotation, fee update endpoints"
```

---

## What We Built

| Endpoint | Purpose |
|----------|---------|
| `PUT /v1/merchants/{id}/webhook` | Change webhook delivery URL |
| `POST /v1/merchants/{id}/webhook/rotate-secret` | Get new HMAC signing secret |
| `PUT /v1/merchants/{id}/fees` | Change MDR% and settlement schedule |

---

## Next Step

→ Continue to **Phase 5 Part 5: Swagger & Full Testing**
