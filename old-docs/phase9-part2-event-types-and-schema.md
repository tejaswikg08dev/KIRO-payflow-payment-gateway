# Hands-On Guide — Phase 9 Part 2: Event Types & Schema

## Goal
- Define all webhook event types
- JSON payload format for each event
- Understanding of when each event fires

---

## Webhook Event Types

| Event | When It Fires | Merchant Action |
|-------|--------------|-----------------|
| `payment.authorized` | Bank approves payment | Show "processing" to customer |
| `payment.captured` | Merchant captures payment | Fulfill order, ship product |
| `payment.failed` | Bank declines or error | Show error, offer retry |
| `payment.voided` | Merchant cancels auth | Update order as cancelled |
| `refund.created` | Refund processed | Notify customer "refund done" |
| `settlement.processed` | Daily settlement complete | Update accounting |

---

## Payload Format

```json
{
  "id": "evt_a1b2c3d4e5f6",
  "event": "payment.captured",
  "created_at": "2026-07-20T15:00:00Z",
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "captured",
    "method": "card",
    "card": {
      "last4": "1111",
      "network": "visa"
    },
    "captured_at": "2026-07-20T15:00:00Z"
  }
}
```

---

## HTTP Delivery Format

```http
POST https://merchant.com/webhooks HTTP/1.1
Content-Type: application/json
X-PayFlow-Signature: sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
X-PayFlow-Timestamp: 1721484000
X-PayFlow-Event: payment.captured
User-Agent: PayFlow-Webhook/1.0

{...payload JSON...}
```

---

## Next Step → Phase 9 Part 3
