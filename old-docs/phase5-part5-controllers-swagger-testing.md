# Hands-On Guide — Phase 5 Part 5: Swagger & Full Testing

## Goal

By the end of Part 5, you will have:
- Complete Swagger UI with all merchant endpoints documented
- Full end-to-end test flow (register user → create merchant → generate key → use key)
- Postman collection for merchant-service
- Phase 5 COMPLETE
- Git commit

## Prerequisites

- Parts 1-4 completed (all merchant endpoints working)
- identity-service running (for user registration)

---

## Step 5.1: Open Swagger UI

Start merchant-service and open: **http://localhost:8082/swagger-ui.html**

You should see all endpoints grouped under "Merchants":
```
POST   /v1/merchants                          Register a new merchant
GET    /v1/merchants/{merchantId}             Get merchant by ID
POST   /v1/merchants/{merchantId}/api-keys    Generate API key pair
GET    /v1/merchants/{merchantId}/api-keys    List active API keys
POST   /v1/merchants/{merchantId}/api-keys/{keyId}/revoke   Revoke key
PUT    /v1/merchants/{merchantId}/webhook     Update webhook URL
POST   /v1/merchants/{merchantId}/webhook/rotate-secret     Rotate secret
PUT    /v1/merchants/{merchantId}/fees        Update fee config
```

Click "Try it out" on any endpoint to test directly from the browser.

---

## Step 5.2: Full End-to-End Test Flow

Run these in order to simulate a real merchant onboarding:

### 1. Register user (identity-service, port 8081):
```cmd
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"shop@techshop.in\",\"password\":\"ShopPass123!\",\"fullName\":\"TechShop Admin\",\"role\":\"MERCHANT\"}"
```
Save the `userId` from response.

### 2. Create merchant (merchant-service, port 8082):
```cmd
curl -X POST http://localhost:8082/v1/merchants ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\":\"usr_FROM_STEP1\",\"businessName\":\"TechShop India Pvt Ltd\",\"businessType\":\"COMPANY\",\"gstNumber\":\"27AABCU9603R1ZM\",\"websiteUrl\":\"https://techshop.in\",\"webhookUrl\":\"https://techshop.in/webhooks\",\"bankAccountNumber\":\"1234567890\",\"bankIfscCode\":\"HDFC0001234\",\"bankAccountHolder\":\"TechShop India Pvt Ltd\"}"
```
Save the `id` (merchant_id) from response.

### 3. Generate LIVE API key:
```cmd
curl -X POST "http://localhost:8082/v1/merchants/MERCHANT_ID_HERE/api-keys?keyType=LIVE"
```
Save `secret_key` — you'll use this for ALL payment API calls!

### 4. Verify key works (will be used in Phase 6):
```
The secret key (sk_pay_xxx) will be sent as:
  Header: X-Api-Key: sk_pay_xxx
in every payment API call to identify this merchant.
```

### 5. Set up webhook URL:
```cmd
curl -X PUT http://localhost:8082/v1/merchants/MERCHANT_ID_HERE/webhook ^
  -H "Content-Type: application/json" ^
  -d "{\"webhookUrl\":\"https://techshop.in/webhooks/payflow\"}"
```

---

## Step 5.3: Postman Collection

Create collection: **PayFlow Merchant Service**

```
📁 PayFlow Merchant Service
├── 📋 Environment Variables:
│   ├── base_url: http://localhost:8082
│   ├── merchant_id: (set after create)
│   └── api_secret_key: (set after generate)
│
├── POST Create Merchant
│   URL: {{base_url}}/v1/merchants
│   Body: { businessName, businessType, userId, ... }
│   Tests: pm.environment.set("merchant_id", response.data.id)
│
├── GET Get Merchant
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}
│
├── POST Generate API Key
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}/api-keys?keyType=LIVE
│   Tests: pm.environment.set("api_secret_key", response.data.secret_key)
│
├── GET List API Keys
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}/api-keys
│
├── POST Revoke API Key
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}/api-keys/{{key_id}}/revoke
│
├── PUT Update Webhook URL
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}/webhook
│   Body: { "webhookUrl": "https://newurl.com/webhooks" }
│
├── POST Rotate Webhook Secret
│   URL: {{base_url}}/v1/merchants/{{merchant_id}}/webhook/rotate-secret
│
└── PUT Update Fees
    URL: {{base_url}}/v1/merchants/{{merchant_id}}/fees
    Body: { "mdrPercentage": 1.80, "settlementSchedule": "T+1" }
```

---

## Step 5.4: Git Commit

```cmd
git add .
git commit -m "Phase 5 Complete: Merchant service - onboarding, API keys, webhooks, fees"
```

---

## Phase 5 Complete! 🎉

| Part | What We Built |
|------|--------------|
| Part 1 | Project setup, Flyway migration, Merchant + ApiKey entities |
| Part 2 | MerchantService (create, get), MerchantController, webhook secret generation |
| Part 3 | API key validation (SHA-256 hash), listing, revocation |
| Part 4 | Webhook URL update, secret rotation, fee configuration |
| Part 5 | Swagger UI, end-to-end test flow, Postman collection |

**Merchant Service is fully working.** A merchant can:
- Register their business
- Get API keys (test + live)
- Configure webhook URL
- Set fee plans
- Rotate keys/secrets when needed

---

## Interview Notes

**Q: "How do you authenticate merchants in API calls?"**
> "Merchants send their secret key in X-Api-Key header. We compute SHA-256 hash of the incoming key and look it up in the api_keys table. If found and status is ACTIVE, we extract the merchant_id and attach it to the request for downstream services."

**Q: "Why SHA-256 for API keys but BCrypt for passwords?"**
> "API keys are generated by us with 32+ random characters — already impossible to brute-force. SHA-256 is fast (microseconds) which is important for per-request validation. Passwords are chosen by humans and may be weak, so BCrypt adds intentional slowness (250ms) to resist brute-force attacks."

**Q: "How do you handle key rotation?"**
> "Generate new key → merchant updates their application → revoke old key. The revocation is immediate — any request with the old key gets 401. We keep revoked keys in DB for audit trail."

---

## Next Step

→ Move to **Phase 6: Payment Service (Core)**
→ Start with **`phase6-part1-project-setup-and-database.md`**

Phase 6 is the heart of the system — payment orders, authorization, capture, void, refund, idempotency, and state machine.
