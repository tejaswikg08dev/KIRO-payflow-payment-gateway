# PayFlow — Complete API Reference

**Document Version:** 2.0  
**Last Updated:** August 2026  
**Base URL:** `http://localhost:8080` (local) | `https://api.payflow.com` (production)

---

## Authentication

All API calls require authentication via one of:
- **JWT Token:** `Authorization: Bearer <token>` (Dashboard users)
- **API Key:** `X-Api-Key: sk_live_xxx` (Server-to-server)

---

## 1. Authentication APIs

### POST /v1/auth/register
Create a new user account.

**Request:**
```json
{
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "userId": "usr_abc123",
    "email": "john@example.com"
  }
}
```

### POST /v1/auth/login
Authenticate and get JWT tokens.

**Request:**
```json
{
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "expiresIn": 900
  }
}
```

### POST /v1/auth/refresh
Get new access token using refresh token.

---

## 2. Merchant APIs

### POST /v1/merchants
Create a new merchant account.

**Request:**
```json
{
  "businessName": "Acme Corp",
  "businessType": "ECOMMERCE",
  "email": "business@acme.com",
  "phone": "+919876543210",
  "website": "https://acme.com"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "merch_abc123",
    "businessName": "Acme Corp",
    "status": "PENDING"
  }
}
```

### POST /v1/merchants/{id}/api-keys
Generate new API keys.

**Response (201):**
```json
{
  "success": true,
  "data": {
    "testKey": "sk_test_xxxxxxxxxxxxx",
    "liveKey": "sk_live_xxxxxxxxxxxxx"
  }
}
```

### PUT /v1/merchants/{id}/webhook
Configure webhook URL.

**Request:**
```json
{
  "webhookUrl": "https://acme.com/webhooks/payflow",
  "events": ["payment.authorized", "payment.captured", "refund.processed"]
}
```

---

## 3. Order APIs

### POST /v1/orders
Create a payment order.


**Headers:**
```
X-Api-Key: sk_live_xxxxx
X-Idempotency-Key: unique-request-id
```

**Request:**
```json
{
  "amount": 10000,
  "currency": "INR",
  "description": "Order #12345",
  "merchantOrderId": "ORD-12345",
  "customerEmail": "customer@email.com",
  "customerPhone": "+919876543210",
  "metadata": {
    "productId": "PROD-001"
  }
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "ord_abc123def456",
    "amount": 10000,
    "currency": "INR",
    "status": "CREATED",
    "checkoutUrl": "https://checkout.payflow.com/ord_abc123def456",
    "expiresAt": "2026-08-04T13:00:00Z"
  }
}
```

### GET /v1/orders/{id}
Get order details.

### GET /v1/orders
List orders with pagination.

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)
- `status` (optional)
- `from` (date)
- `to` (date)

---

## 4. Payment APIs

### POST /v1/payments
Process a payment.

**Request (Card):**
```json
{
  "orderId": "ord_abc123def456",
  "paymentMethod": "CARD",
  "card": {
    "number": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2028",
    "cvv": "123",
    "holderName": "John Doe"
  }
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "pay_xyz789",
    "orderId": "ord_abc123def456",
    "amount": 10000,
    "status": "AUTHORIZED",
    "authCode": "AUTH123",
    "fraudScore": 25
  }
}
```

### POST /v1/payments/{id}/capture
Capture an authorized payment.

**Request:**
```json
{
  "amount": 10000
}
```

### POST /v1/payments/{id}/void
Void an authorized payment (cancel before capture).

### POST /v1/payments/{id}/refund
Refund a captured payment.

**Request:**
```json
{
  "amount": 5000,
  "reason": "Customer requested partial refund"
}
```

### GET /v1/payments/{id}
Get payment details.

---

## 5. Settlement APIs

### GET /v1/settlements
List settlements.

### GET /v1/settlements/{id}
Get settlement details.

### GET /v1/settlements/{id}/report
Download settlement report (PDF/CSV).

---

## 6. Webhook APIs

### GET /v1/webhooks/events
List webhook events.

### POST /v1/webhooks/events/{id}/retry
Retry a failed webhook delivery.

---

## 7. Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `INVALID_REQUEST` | 400 | Invalid request parameters |
| `AUTHENTICATION_FAILED` | 401 | Invalid credentials |
| `UNAUTHORIZED` | 403 | Access denied |
| `NOT_FOUND` | 404 | Resource not found |
| `DUPLICATE_REQUEST` | 409 | Idempotency key already used |
| `RATE_LIMITED` | 429 | Too many requests |
| `PAYMENT_FAILED` | 400 | Payment declined by bank |
| `INVALID_CARD` | 400 | Card validation failed |
| `INSUFFICIENT_FUNDS` | 400 | Card has insufficient funds |
| `INTERNAL_ERROR` | 500 | Server error |

---

## 8. Webhook Events

### Event Types

| Event | Description |
|-------|-------------|
| `payment.authorized` | Payment was authorized |
| `payment.captured` | Payment was captured |
| `payment.failed` | Payment failed |
| `payment.voided` | Payment was voided |
| `refund.processed` | Refund was processed |
| `settlement.completed` | Settlement completed |

### Webhook Payload

```json
{
  "id": "evt_abc123",
  "type": "payment.authorized",
  "created": "2026-08-04T12:00:00Z",
  "data": {
    "paymentId": "pay_xyz789",
    "orderId": "ord_abc123def456",
    "amount": 10000,
    "status": "AUTHORIZED"
  }
}
```

### Webhook Signature

```
X-Payflow-Signature: sha256=xxxxxxxxx
```

Verify with: `HMAC-SHA256(payload, webhook_secret)`

---

## Next Document

**Continue to:** [lld-complete.md](./lld-complete.md) — Low-Level Design

---

**End of API Reference**
