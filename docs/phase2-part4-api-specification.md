# Phase 2 — Part 4: API Specification

> Complete REST API specification for ALL services.
> Every endpoint with HTTP method, URL, headers, request body, response body,
> status codes, and examples. This is what Swagger UI will show.

---

## 1. API Design Principles

Before we define endpoints, here are the rules we follow:

### 1.1 URL Structure

```
https://api.payflow.com/v1/{resource}/{id}/{action}

Examples:
POST   /v1/orders                    ← Create an order
GET    /v1/orders/ord_abc123         ← Get order by ID
POST   /v1/payments                  ← Create a payment
POST   /v1/payments/pay_xyz/capture  ← Capture a payment
GET    /v1/payments?merchant_id=xxx  ← List payments (with filters)
POST   /v1/payments/pay_xyz/refund   ← Refund a payment
```

### 1.2 Naming Rules

| Rule | Example |
|------|---------|
| Use lowercase | `/v1/payments` not `/v1/Payments` |
| Use hyphens for multi-word | `/v1/api-keys` not `/v1/apiKeys` |
| Use plural nouns | `/v1/orders` not `/v1/order` |
| Actions as sub-resource | `/v1/payments/{id}/capture` not `/v1/capture-payment` |
| Version in URL | `/v1/` prefix on all endpoints |

### 1.3 Authentication

| Who | Method | Header |
|-----|--------|--------|
| Merchant (API calls) | API Key | `X-Api-Key: sk_pay_xxxxx` |
| Dashboard user | JWT Bearer | `Authorization: Bearer eyJhbGc...` |
| Internal service | Internal JWT | `X-Internal-Token: eyJ...` |

### 1.4 Standard Response Format

**Success:**
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

**Error:**
```json
{
  "success": false,
  "error": {
    "code": "PAYMENT_DECLINED",
    "message": "Payment was declined due to insufficient funds",
    "details": { "bank_response_code": "51" }
  },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

**Paginated List:**
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "total": 150,
    "page": 1,
    "per_page": 20,
    "total_pages": 8
  },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

---

## 2. Identity Service APIs (Port 8081)

### POST /v1/auth/register — Create a new user account

```
URL:     POST http://localhost:8081/v1/auth/register
Headers: Content-Type: application/json
Auth:    None (public endpoint)
```

**Request Body:**
```json
{
  "email": "merchant@example.com",
  "password": "SecureP@ss123",
  "full_name": "Rajesh Kumar",
  "phone": "+919876543210",
  "role": "MERCHANT"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "user_id": "usr_a1b2c3d4e5f6",
    "email": "merchant@example.com",
    "full_name": "Rajesh Kumar",
    "role": "MERCHANT",
    "email_verified": false,
    "status": "ACTIVE",
    "created_at": "2026-07-19T14:30:00Z"
  }
}
```

**Error (409 Conflict — email already exists):**
```json
{
  "success": false,
  "error": {
    "code": "DUPLICATE_EMAIL",
    "message": "A user with this email already exists"
  }
}
```

---

### POST /v1/auth/login — Login and get JWT tokens

```
URL:     POST http://localhost:8081/v1/auth/login
Headers: Content-Type: application/json
Auth:    None (public endpoint)
```

**Request Body:**
```json
{
  "email": "merchant@example.com",
  "password": "SecureP@ss123"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": "usr_a1b2c3d4e5f6",
      "email": "merchant@example.com",
      "full_name": "Rajesh Kumar",
      "role": "MERCHANT"
    }
  }
}
```

**Error (401 — wrong password):**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Email or password is incorrect"
  }
}
```

---

### POST /v1/auth/refresh — Get new access token using refresh token

```
URL:     POST http://localhost:8081/v1/auth/refresh
Headers: Content-Type: application/json
Auth:    None
```

**Request Body:**
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...(new)",
    "expires_in": 900
  }
}
```


---

## 3. Merchant Service APIs (Port 8082)

### POST /v1/merchants — Register a new merchant

```
URL:     POST http://localhost:8082/v1/merchants
Headers: Content-Type: application/json
         Authorization: Bearer {jwt_token}
Auth:    JWT (user must be logged in with role MERCHANT)
```

**Request Body:**
```json
{
  "business_name": "TechShop India Pvt Ltd",
  "business_type": "COMPANY",
  "registration_number": "U72200MH2020PTC123456",
  "gst_number": "27AABCU9603R1ZM",
  "website_url": "https://techshop.in",
  "callback_url": "https://techshop.in/payment/callback",
  "webhook_url": "https://techshop.in/webhooks/payflow",
  "bank_account_number": "1234567890123456",
  "bank_ifsc_code": "HDFC0001234",
  "bank_account_holder": "TechShop India Pvt Ltd"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "merchant_id": "merch_xyz789",
    "business_name": "TechShop India Pvt Ltd",
    "status": "PENDING",
    "kyc_verified": false,
    "settlement_schedule": "T+2",
    "mdr_percentage": 2.00,
    "webhook_url": "https://techshop.in/webhooks/payflow",
    "created_at": "2026-07-19T14:30:00Z"
  }
}
```

---

### POST /v1/merchants/{merchant_id}/api-keys — Generate API keys

```
URL:     POST http://localhost:8082/v1/merchants/merch_xyz789/api-keys
Headers: Authorization: Bearer {jwt_token}
Auth:    JWT (must be the merchant owner)
```

**Request Body:**
```json
{
  "key_type": "LIVE"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "key_id": "key_abc123",
    "key_type": "LIVE",
    "public_key": "pk_pay_51a2b3c4d5e6f7g8h9",
    "secret_key": "sk_pay_9h8g7f6e5d4c3b2a1_SHOW_ONCE",
    "status": "ACTIVE",
    "created_at": "2026-07-19T14:30:00Z",
    "note": "⚠️ Save the secret_key now. It will NOT be shown again."
  }
}
```

> **IMPORTANT:** The `secret_key` is shown ONLY ONCE in this response.
> We store only the SHA-256 hash in our database. If merchant loses it,
> they must generate new keys.

---

## 4. Payment Service APIs (Port 8083)

### POST /v1/orders — Create a payment order

**What this does:** Merchant creates an order BEFORE customer pays.
It reserves an order_id that the payment will be linked to.

```
URL:     POST http://localhost:8083/v1/orders
Headers: Content-Type: application/json
         X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
Auth:    API Key (merchant's secret key)
```

**Request Body:**
```json
{
  "amount": 5000.00,
  "currency": "INR",
  "receipt": "order_12345",
  "notes": {
    "product": "Wireless Headphones",
    "customer_email": "buyer@gmail.com"
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "receipt": "order_12345",
    "status": "CREATED",
    "expires_at": "2026-07-19T15:00:00Z",
    "notes": {
      "product": "Wireless Headphones",
      "customer_email": "buyer@gmail.com"
    },
    "created_at": "2026-07-19T14:30:00Z"
  }
}
```

---

### POST /v1/payments — Authorize a payment (Card)

**What this does:** Customer submits card details. We authorize with bank.
Bank holds the money on customer's card (doesn't deduct yet).

```
URL:     POST http://localhost:8083/v1/payments
Headers: Content-Type: application/json
         X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
         Idempotency-Key: idem_unique_key_12345
Auth:    API Key
```

**Request Body (Card Payment):**
```json
{
  "order_id": "ord_LkR3d9xF2m",
  "amount": 5000.00,
  "currency": "INR",
  "method": "card",
  "card": {
    "number": "4111111111111111",
    "expiry_month": 12,
    "expiry_year": 2028,
    "cvv": "123",
    "holder_name": "RAJESH KUMAR"
  }
}
```

**Response (201 — Payment Authorized):**
```json
{
  "success": true,
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "authorized",
    "method": "card",
    "card": {
      "last4": "1111",
      "network": "visa",
      "type": "credit",
      "issuer_bank": "HDFC Bank"
    },
    "auth_code": "A1B2C3",
    "rrn": "987654321012",
    "risk_score": 25,
    "route_used": "HDFC_ACQ_01",
    "authorized_at": "2026-07-19T14:30:02Z",
    "created_at": "2026-07-19T14:30:00Z"
  }
}
```

**Response (422 — Payment Declined):**
```json
{
  "success": false,
  "error": {
    "code": "PAYMENT_DECLINED",
    "message": "Payment was declined by the issuing bank",
    "details": {
      "payment_id": "pay_Hk7mN3xQp2",
      "reason": "insufficient_funds",
      "bank_response_code": "51",
      "bank_message": "Insufficient funds in account"
    }
  }
}
```

---

### POST /v1/payments — Authorize a payment (UPI)

**Request Body (UPI Payment):**
```json
{
  "order_id": "ord_LkR3d9xF2m",
  "amount": 5000.00,
  "currency": "INR",
  "method": "upi",
  "upi": {
    "vpa": "rajesh@okicici"
  }
}
```

**Response (201 — Payment Authorized):**
```json
{
  "success": true,
  "data": {
    "payment_id": "pay_Uk9pL2wRs1",
    "status": "authorized",
    "method": "upi",
    "upi": {
      "vpa": "rajesh@okicici"
    },
    "authorized_at": "2026-07-19T14:30:05Z"
  }
}
```

---

### POST /v1/payments/{id}/capture — Capture an authorized payment

**What this does:** Merchant confirms they want the money. Bank deducts from customer.

```
URL:     POST http://localhost:8083/v1/payments/pay_Hk7mN3xQp2/capture
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
Auth:    API Key
```

**Request Body:**
```json
{
  "amount": 5000.00
}
```
> Note: `amount` can be less than authorized (partial capture).
> E.g., authorized ₹500 for Uber ride, capture only ₹320.

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "status": "captured",
    "amount": 5000.00,
    "captured_amount": 5000.00,
    "captured_at": "2026-07-19T15:00:00Z"
  }
}
```

**Error (400 — Invalid state):**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE_TRANSITION",
    "message": "Payment cannot be captured. Current status: 'failed'. Capture only works on 'authorized' payments."
  }
}
```

---

### POST /v1/payments/{id}/void — Cancel authorization (release hold)

```
URL:     POST http://localhost:8083/v1/payments/pay_Hk7mN3xQp2/void
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "status": "voided",
    "message": "Authorization cancelled. Hold released on customer's card.",
    "voided_at": "2026-07-19T15:00:00Z"
  }
}
```

---

### POST /v1/payments/{id}/refund — Refund a captured payment

```
URL:     POST http://localhost:8083/v1/payments/pay_Hk7mN3xQp2/refund
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Request Body:**
```json
{
  "amount": 2000.00,
  "reason": "Customer returned the product"
}
```
> Note: `amount` can be partial. Refund ₹2000 out of ₹5000 payment.

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "refund_id": "rfnd_Qm4nP8wXv3",
    "payment_id": "pay_Hk7mN3xQp2",
    "amount": 2000.00,
    "status": "processed",
    "reason": "Customer returned the product",
    "rrn": "123456789012",
    "created_at": "2026-07-19T16:00:00Z"
  }
}
```

---

### GET /v1/payments/{id} — Get payment details

```
URL:     GET http://localhost:8083/v1/payments/pay_Hk7mN3xQp2
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "captured",
    "method": "card",
    "card": {
      "last4": "1111",
      "network": "visa",
      "type": "credit"
    },
    "auth_code": "A1B2C3",
    "rrn": "987654321012",
    "risk_score": 25,
    "captured_amount": 5000.00,
    "refunded_amount": 2000.00,
    "authorized_at": "2026-07-19T14:30:02Z",
    "captured_at": "2026-07-19T15:00:00Z",
    "created_at": "2026-07-19T14:30:00Z"
  }
}
```

---

### GET /v1/payments — List payments (with filters & pagination)

```
URL:     GET http://localhost:8083/v1/payments?status=captured&page=1&per_page=20
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Query Parameters:**
| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| status | string | Filter by status | `authorized`, `captured`, `settled` |
| method | string | Filter by payment method | `card`, `upi`, `netbanking` |
| from | date | Start date | `2026-07-01` |
| to | date | End date | `2026-07-19` |
| page | int | Page number (default 1) | `1` |
| per_page | int | Items per page (default 20, max 100) | `20` |

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "payment_id": "pay_Hk7mN3xQp2",
      "amount": 5000.00,
      "status": "captured",
      "method": "card",
      "card": { "last4": "1111", "network": "visa" },
      "created_at": "2026-07-19T14:30:00Z"
    },
    {
      "payment_id": "pay_Uk9pL2wRs1",
      "amount": 1200.00,
      "status": "captured",
      "method": "upi",
      "upi": { "vpa": "rajesh@okicici" },
      "created_at": "2026-07-19T13:00:00Z"
    }
  ],
  "pagination": {
    "total": 156,
    "page": 1,
    "per_page": 20,
    "total_pages": 8
  }
}
```

---

## 5. Settlement Service APIs (Port 8085)

### GET /v1/settlements — List settlements for merchant

```
URL:     GET http://localhost:8085/v1/settlements?page=1
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "settlement_id": "stl_Mn2kP9wQr5",
      "settlement_date": "2026-07-18",
      "gross_amount": 45000.00,
      "refund_amount": 5000.00,
      "fee_amount": 800.00,
      "gst_on_fee": 144.00,
      "net_amount": 39056.00,
      "total_transactions": 12,
      "total_refunds": 2,
      "status": "completed",
      "payout_utr": "HDFC2026071800123",
      "processed_at": "2026-07-19T00:30:00Z"
    }
  ],
  "pagination": { "total": 30, "page": 1, "per_page": 20, "total_pages": 2 }
}
```

---

## 6. Webhook Service APIs (Port 8086)

### GET /v1/webhooks/events — List webhook events (for debugging)

```
URL:     GET http://localhost:8086/v1/webhooks/events?status=failed
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "event_id": "evt_Xp3mR7wNq1",
      "event_type": "payment.captured",
      "delivery_status": "failed",
      "attempt_count": 3,
      "last_attempt_at": "2026-07-19T15:30:00Z",
      "next_retry_at": "2026-07-19T17:30:00Z",
      "response_code": 500,
      "created_at": "2026-07-19T15:00:00Z"
    }
  ]
}
```

### POST /v1/webhooks/events/{id}/retry — Manually retry a failed webhook

```
URL:     POST http://localhost:8086/v1/webhooks/events/evt_Xp3mR7wNq1/retry
Headers: X-Api-Key: sk_pay_9h8g7f6e5d4c3b2a1
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "event_id": "evt_Xp3mR7wNq1",
    "message": "Retry scheduled. Will attempt delivery within 60 seconds."
  }
}
```

---

## 7. HTTP Status Codes Used

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Successful GET, PUT, or action (capture, void) |
| 201 | Created | Successful POST (new resource created) |
| 400 | Bad Request | Invalid input (missing field, wrong format) |
| 401 | Unauthorized | Missing or invalid API key / JWT |
| 403 | Forbidden | Valid auth but no permission for this resource |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate (email exists, order already paid) |
| 422 | Unprocessable | Business rule violation (payment declined, invalid state) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |
| 502 | Bad Gateway | Upstream service failed (bank timeout) |
| 503 | Service Unavailable | Circuit breaker open, service down |

---

## 8. Error Codes Reference

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| INVALID_CREDENTIALS | 401 | Wrong email/password |
| INVALID_API_KEY | 401 | API key not found or revoked |
| TOKEN_EXPIRED | 401 | JWT token has expired |
| RATE_LIMIT_EXCEEDED | 429 | Too many requests per second |
| DUPLICATE_EMAIL | 409 | Email already registered |
| DUPLICATE_IDEMPOTENCY | 409 | Same idempotency key with different body |
| PAYMENT_DECLINED | 422 | Bank declined the payment |
| INVALID_STATE_TRANSITION | 400 | Can't perform action in current state |
| INSUFFICIENT_FUNDS | 422 | Customer doesn't have enough money |
| CARD_EXPIRED | 422 | Card expiry date has passed |
| ORDER_EXPIRED | 400 | Order timed out (30 min) |
| MERCHANT_NOT_ACTIVE | 403 | Merchant account not yet activated |
| AMOUNT_EXCEEDS_AUTHORIZED | 400 | Capture amount > authorized amount |
| REFUND_EXCEEDS_CAPTURED | 400 | Refund amount > captured - already refunded |

---

## Next Step

→ Continue to **`phase2-part5-iso8583-message-specification.md`**
