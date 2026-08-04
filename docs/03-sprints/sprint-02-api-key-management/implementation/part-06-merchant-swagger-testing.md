# Sprint 2, Part 06: Merchant Service Swagger Testing

**Duration:** 30 minutes  
**Prerequisites:** Parts 01-05 completed, services running  
**Goal:** Test all merchant service endpoints using Swagger UI

---

## 1. Learning Objectives

By the end of this part, you will:
- Access and navigate Swagger UI for merchant-service
- Test all API key management endpoints
- Test webhook configuration endpoints
- Verify error handling and validation

---

## 2. Start Services

```powershell
# Terminal 1: Infrastructure
docker compose -f docker-compose-infra.yml up -d

# Terminal 2: Service Registry
cd service-registry && mvn spring-boot:run

# Terminal 3: Merchant Service
cd merchant-service && mvn spring-boot:run
```

---

## 3. Access Swagger UI

Open in browser: **http://localhost:8082/swagger-ui/index.html**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SWAGGER UI LAYOUT                                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Merchant Service API                                                │   │
│  │  Version: 1.0.0                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Merchants                                                    [Expand ▼]    │
│  ├── POST   /v1/merchants                Register a new merchant           │
│  ├── GET    /v1/merchants/{merchantId}   Get merchant by ID                │
│  ├── POST   /v1/merchants/{merchantId}/api-keys   Generate API key         │
│  ├── GET    /v1/merchants/{merchantId}/api-keys   List all API keys        │
│  ├── DELETE /v1/merchants/{merchantId}/api-keys/{keyId}   Revoke key       │
│  ├── PUT    /v1/merchants/{merchantId}/webhook   Update webhook            │
│  └── GET    /v1/merchants/{merchantId}/webhook   Get webhook config        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Test Scenarios

### 4.1 Create a Merchant (if not exists)

**Endpoint:** `POST /v1/merchants`

**Request Body:**
```json
{
  "userId": "usr_swagger_test",
  "businessName": "Swagger Test Store",
  "businessType": "INDIVIDUAL"
}
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "merch_xxxxxxxxxx",
    "userId": "usr_swagger_test",
    "businessName": "Swagger Test Store",
    "businessType": "INDIVIDUAL",
    "webhookSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "mdrPercentage": 2.00,
    "settlementSchedule": "T+2",
    "status": "PENDING",
    "kycVerified": false,
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

**Save the `id` value (e.g., `merch_xxxxxxxxxx`) for subsequent tests.**

---

### 4.2 Generate TEST API Key

**Endpoint:** `POST /v1/merchants/{merchantId}/api-keys?keyType=TEST`

**Path Parameters:**
- `merchantId`: The ID from step 4.1

**Query Parameters:**
- `keyType`: TEST

**Expected Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "key_id": "key_xxxxxxxxxx",
    "key_type": "TEST",
    "public_key": "pk_test_xxxxxxxxxxxxxxxxxxxx",
    "secret_key": "sk_test_EXAMPLE_DO_NOT_USE_xxxxxxxxxxxx",
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

⚠️ **Copy the `secret_key` immediately!** It won't be shown again.

---

### 4.3 Generate LIVE API Key

**Endpoint:** `POST /v1/merchants/{merchantId}/api-keys?keyType=LIVE`

**Expected Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "key_id": "key_yyyyyyyyyy",
    "key_type": "LIVE",
    "public_key": "pk_live_xxxxxxxxxxxxxxxxxxxx",
    "secret_key": "sk_live_EXAMPLE_DO_NOT_USE_xxxxxxxxxxxx",
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

---

### 4.4 List All API Keys

**Endpoint:** `GET /v1/merchants/{merchantId}/api-keys`

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "keyId": "key_xxxxxxxxxx",
      "keyType": "TEST",
      "publicKey": "pk_test_xxxxxxxxxxxxxxxxxxxx",
      "keyPrefix": "sk_test_xxxx",
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:30:00Z"
    },
    {
      "keyId": "key_yyyyyyyyyy",
      "keyType": "LIVE",
      "publicKey": "pk_live_xxxxxxxxxxxxxxxxxxxx",
      "keyPrefix": "sk_live_xxxx",
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:31:00Z"
    }
  ]
}
```

**Verify:**
- ✅ Both keys appear in the list
- ✅ `keyPrefix` shows partial secret (first 12 chars)
- ✅ Full `secret_key` is NOT returned
- ✅ Both have `status: "ACTIVE"`

---

### 4.5 Revoke API Key

**Endpoint:** `DELETE /v1/merchants/{merchantId}/api-keys/{keyId}`

**Path Parameters:**
- `merchantId`: Your merchant ID
- `keyId`: The TEST key ID from step 4.2

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "message": "API key revoked successfully",
    "key_id": "key_xxxxxxxxxx"
  }
}
```

---

### 4.6 Verify Revocation

**Endpoint:** `GET /v1/merchants/{merchantId}/api-keys`

**Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "keyId": "key_xxxxxxxxxx",
      "keyType": "TEST",
      "publicKey": "pk_test_xxxxxxxxxxxxxxxxxxxx",
      "keyPrefix": "sk_test_xxxx",
      "status": "REVOKED",          ← Status changed
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:30:00Z"
    },
    {
      "keyId": "key_yyyyyyyyyy",
      "keyType": "LIVE",
      "publicKey": "pk_live_xxxxxxxxxxxxxxxxxxxx",
      "keyPrefix": "sk_live_xxxx",
      "status": "ACTIVE",
      "lastUsedAt": null,
      "createdAt": "2024-01-15T10:31:00Z"
    }
  ]
}
```

---

### 4.7 Test Revoke Already Revoked Key

**Endpoint:** `DELETE /v1/merchants/{merchantId}/api-keys/{keyId}`

**Use the same (already revoked) key ID**

**Expected Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "BAD_REQUEST",
    "message": "API key is already revoked"
  }
}
```

---

### 4.8 Update Webhook URL

**Endpoint:** `PUT /v1/merchants/{merchantId}/webhook`

**Request Body:**
```json
{
  "webhookUrl": "https://api.mystore.com/webhooks/payflow"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "webhookUrl": "https://api.mystore.com/webhooks/payflow",
    "webhookSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  }
}
```

**Save the `webhookSecret` for signature verification.**

---

### 4.9 Get Webhook Configuration

**Endpoint:** `GET /v1/merchants/{merchantId}/webhook`

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "webhookUrl": "https://api.mystore.com/webhooks/payflow",
    "webhookSecret": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  }
}
```

---

### 4.10 Verify Secret Regeneration

**Endpoint:** `PUT /v1/merchants/{merchantId}/webhook`

**Request Body (different URL):**
```json
{
  "webhookUrl": "https://api.mystore.com/v2/webhooks"
}
```

**Verify:** `webhookSecret` is DIFFERENT from step 4.8

---

## 5. Error Scenarios to Test

### 5.1 Invalid Merchant ID

**Endpoint:** `GET /v1/merchants/merch_invalid`

**Expected Response (404 Not Found):**
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Merchant not found with id: merch_invalid"
  }
}
```

### 5.2 Invalid Key Type

**Endpoint:** `POST /v1/merchants/{merchantId}/api-keys?keyType=INVALID`

**Expected Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "BAD_REQUEST",
    "message": "No enum constant com.payflow.merchant.model.ApiKey.KeyType.INVALID"
  }
}
```

### 5.3 Revoke Non-existent Key

**Endpoint:** `DELETE /v1/merchants/{merchantId}/api-keys/key_nonexistent`

**Expected Response (404 Not Found):**
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "ApiKey not found with id: key_nonexistent"
  }
}
```

---

## 6. Test Checklist

| Test | Endpoint | Expected | Pass? |
|------|----------|----------|-------|
| Create merchant | POST /merchants | 201 + merchant data | ☐ |
| Generate TEST key | POST /api-keys?keyType=TEST | 201 + secret shown | ☐ |
| Generate LIVE key | POST /api-keys?keyType=LIVE | 201 + secret shown | ☐ |
| List keys | GET /api-keys | 200 + both keys | ☐ |
| Revoke key | DELETE /api-keys/{id} | 200 + success | ☐ |
| List after revoke | GET /api-keys | 200 + REVOKED status | ☐ |
| Revoke again | DELETE /api-keys/{id} | 400 + already revoked | ☐ |
| Update webhook | PUT /webhook | 200 + new secret | ☐ |
| Get webhook | GET /webhook | 200 + current config | ☐ |
| Update again | PUT /webhook | 200 + DIFFERENT secret | ☐ |

---

## 7. Key Takeaways

| Concept | Verified |
|---------|----------|
| Secret key shown only at creation | ✅ |
| Soft delete for revocation | ✅ |
| Secret regeneration on webhook update | ✅ |
| Proper error responses | ✅ |

---

## 8. Next Steps

**Continue to:** [part-07-frontend-apikeys-page.md](./part-07-frontend-apikeys-page.md)

In the next part, you'll create the React page for API key management.

---

**End of Sprint 2, Part 06**
