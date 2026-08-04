# Sprint 2, Part 04: Webhook Configuration

**Duration:** 1 hour  
**Prerequisites:** Part 03 completed  
**Goal:** Add endpoints to configure webhook URLs for payment notifications

---

## 1. Learning Objectives

By the end of this part, you will:
- Understand how webhook signatures work (HMAC-SHA256)
- Add webhook URL update and retrieval endpoints
- Implement automatic secret regeneration for security

---

## 2. Webhook Security Explained

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WEBHOOK SECURITY WITH HMAC                                │
│                                                                              │
│  Problem: How does merchant verify webhook came from PayFlow?               │
│  ─────────────────────────────────────────────────────────────              │
│                                                                              │
│  Without signature:                                                          │
│  ┌─────────────┐        POST /webhooks          ┌─────────────┐            │
│  │  Attacker   │ ─────────────────────────────► │  Merchant   │            │
│  │             │  {"event": "payment.completed"} │             │            │
│  └─────────────┘                                 └─────────────┘            │
│  Merchant thinks it's from PayFlow → processes fake payment!               │
│                                                                              │
│  With HMAC signature:                                                        │
│  ┌─────────────┐        POST /webhooks          ┌─────────────┐            │
│  │  PayFlow    │ ─────────────────────────────► │  Merchant   │            │
│  │             │  X-Webhook-Signature:          │             │            │
│  │             │    sha256=abc123...            │  Verify:    │            │
│  └─────────────┘  Body: {"event": ...}          │  HMAC(body, │            │
│                                                 │  secret)    │            │
│        Merchant's webhookSecret ◄──────────────│  == sig?    │            │
│        (stored securely)                        └─────────────┘            │
│                                                                              │
│  How HMAC signing works:                                                     │
│  ─────────────────────────                                                  │
│  1. PayFlow has merchant's webhookSecret (from DB)                         │
│  2. PayFlow computes: HMAC-SHA256(request_body, webhookSecret)              │
│  3. PayFlow sends signature in X-Webhook-Signature header                   │
│  4. Merchant receives webhook, computes same HMAC                           │
│  5. If signatures match → webhook is authentic                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Webhook Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WEBHOOK NOTIFICATION FLOW                                 │
│                                                                              │
│  Customer pays                                                               │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────┐                                                        │
│  │ Payment Service │                                                        │
│  │                 │  Payment completed                                     │
│  │                 │ ─────────────────►  Event published                   │
│  └─────────────────┘                                                        │
│                                              │                               │
│                                              ▼                               │
│                               ┌─────────────────────────┐                   │
│                               │    Webhook Service      │                   │
│                               │                         │                   │
│                               │  1. Get merchant config │                   │
│                               │  2. Get webhook URL     │                   │
│                               │  3. Get webhook secret  │                   │
│                               │  4. Sign payload        │                   │
│                               │  5. POST to merchant    │                   │
│                               └─────────────────────────┘                   │
│                                              │                               │
│                                              ▼                               │
│                               ┌─────────────────────────┐                   │
│                               │  Merchant's Server      │                   │
│                               │                         │                   │
│                               │  POST /webhooks/payflow │                   │
│                               │  X-Webhook-Signature:   │                   │
│                               │    sha256=abc123...     │                   │
│                               │                         │                   │
│                               │  {                      │                   │
│                               │    "event": "payment.completed",            │
│                               │    "payment_id": "pay_xxx"                  │
│                               │  }                      │                   │
│                               └─────────────────────────┘                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Add Service Methods

### 4.1 Update MerchantService

**File:** `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`

Add these methods:

```java
/**
 * Update webhook URL for a merchant.
 * Regenerates webhook secret on URL change for security.
 */
@Transactional
public WebhookConfig updateWebhook(String merchantId, String webhookUrl) {
    Merchant merchant = getMerchant(merchantId);
    
    merchant.setWebhookUrl(webhookUrl);
    // Regenerate webhook secret on URL change
    String newSecret = generateRandomString(32);
    merchant.setWebhookSecret(newSecret);
    
    merchantRepository.save(merchant);
    log.info("Webhook updated for merchant {}", merchantId);
    
    return new WebhookConfig(webhookUrl, newSecret);
}

/**
 * Get webhook configuration for a merchant.
 */
public WebhookConfig getWebhookConfig(String merchantId) {
    Merchant merchant = getMerchant(merchantId);
    return new WebhookConfig(merchant.getWebhookUrl(), merchant.getWebhookSecret());
}

// DTO for webhook configuration
public record WebhookConfig(String webhookUrl, String webhookSecret) {}
```

**Why regenerate secret on URL change?**
- New URL likely means new server/deployment
- New server needs new secret anyway
- Prevents old server from validating webhooks

---

## 5. Add Controller Endpoints

### 5.1 Update MerchantController

**File:** `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`

Add these endpoints:

```java
// ==================== WEBHOOK ENDPOINTS ====================

@PutMapping("/{merchantId}/webhook")
@Operation(summary = "Update webhook URL")
public ResponseEntity<ApiResponse<MerchantService.WebhookConfig>> updateWebhook(
        @PathVariable String merchantId,
        @RequestBody WebhookRequest request) {
    MerchantService.WebhookConfig config = merchantService.updateWebhook(
            merchantId, request.webhookUrl());
    return ResponseEntity.ok(ApiResponse.success(config));
}

@GetMapping("/{merchantId}/webhook")
@Operation(summary = "Get webhook configuration")
public ResponseEntity<ApiResponse<MerchantService.WebhookConfig>> getWebhook(
        @PathVariable String merchantId) {
    MerchantService.WebhookConfig config = merchantService.getWebhookConfig(merchantId);
    return ResponseEntity.ok(ApiResponse.success(config));
}

// ==================== REQUEST DTOs ====================

public record WebhookRequest(String webhookUrl) {}
```

---

## 6. Testing

### 6.1 Update Webhook URL

```powershell
curl -X PUT http://localhost:8082/v1/merchants/merch_xxxxx/webhook `
  -H "Content-Type: application/json" `
  -d '{"webhookUrl": "https://api.mystore.com/webhooks/payflow"}'
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "webhookUrl": "https://api.mystore.com/webhooks/payflow",
    "webhookSecret": "Xk9mN2pQ7vH3jL5sT8wR1yU4eI6oA0bC"
  }
}
```

### 6.2 Get Webhook Configuration

```powershell
curl http://localhost:8082/v1/merchants/merch_xxxxx/webhook
```

### 6.3 Test Secret Regeneration

```powershell
# Update URL again
curl -X PUT http://localhost:8082/v1/merchants/merch_xxxxx/webhook `
  -H "Content-Type: application/json" `
  -d '{"webhookUrl": "https://api.mystore.com/v2/webhooks"}'
```

**Expected:** Different `webhookSecret` than before

---

## 7. Merchant-Side Verification (Reference)

This is how merchants verify webhooks (for documentation purposes):

```java
// Merchant's server code (Node.js example)
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret) {
    const expectedSignature = 'sha256=' + 
        crypto.createHmac('sha256', secret)
              .update(payload)
              .digest('hex');
    
    return crypto.timingSafeEqual(
        Buffer.from(signature),
        Buffer.from(expectedSignature)
    );
}

// Express handler
app.post('/webhooks/payflow', (req, res) => {
    const signature = req.headers['x-webhook-signature'];
    const payload = JSON.stringify(req.body);
    
    if (!verifyWebhookSignature(payload, signature, WEBHOOK_SECRET)) {
        return res.status(401).send('Invalid signature');
    }
    
    // Process webhook...
    res.status(200).send('OK');
});
```

---

## 8. API Reference

### 8.1 Update Webhook

```
PUT /v1/merchants/{merchantId}/webhook
Content-Type: application/json

Request:
{
  "webhookUrl": "https://api.merchant.com/webhooks"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "webhookUrl": "https://api.merchant.com/webhooks",
    "webhookSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  }
}
```

### 8.2 Get Webhook

```
GET /v1/merchants/{merchantId}/webhook

Response (200 OK):
{
  "success": true,
  "data": {
    "webhookUrl": "https://api.merchant.com/webhooks",
    "webhookSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  }
}
```

---

## 9. Key Takeaways

| Concept | Remember |
|---------|----------|
| **HMAC signing** | Proves webhook authenticity |
| **Secret regeneration** | New URL = new secret |
| **X-Webhook-Signature** | Header format: sha256=xxx |
| **Timing-safe compare** | Prevents timing attacks |

---

## 10. Next Steps

**Continue to:** [part-05-fee-plans.md](./part-05-fee-plans.md)

In the next part, you'll add MDR (Merchant Discount Rate) configuration.

---

**End of Sprint 2, Part 04**
