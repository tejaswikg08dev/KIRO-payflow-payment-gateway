# Phase 2 — Part 6: Event & Message Contracts

> This document defines EVERY message that flows between services via SQS queues
> and every webhook event we send to merchants. Think of this as the "API"
> between services that communicate asynchronously.

---

## 1. Why Do We Need This Document?

When services communicate synchronously (REST API), we defined the contract in
Part 4 (API Specification). But some communication is asynchronous:

```
SYNCHRONOUS (Part 4):
Payment Service ──HTTP POST──► Routing Service
"I need an answer NOW, customer is waiting"

ASYNCHRONOUS (This document):
Payment Service ──SQS Message──► Webhook Service
"Hey, payment was captured. Deliver webhook whenever you can."
Payment Service ──SQS Message──► Notification Service
"Send a confirmation email when you get a chance."
```

**Asynchronous is used when:**
- The sender doesn't need an immediate response
- The receiver can process later (within seconds/minutes)
- We want the sender to be fast (don't wait for email to send)
- We want retry capability (if receiver fails, message stays in queue)

---

## 2. SQS Queues We Create

| Queue Name | Producer | Consumer | Purpose |
|-----------|----------|----------|---------|
| `payflow-payment-events` | Payment Service | Webhook Service | Payment state change events |
| `payflow-webhook-delivery` | Webhook Service | Webhook Service | Retry queue for failed deliveries |
| `payflow-notification` | Payment Service, Settlement Service | Notification Service | Email/SMS triggers |
| `payflow-settlement-trigger` | Scheduler (Lambda/Cron) | Settlement Service | Trigger daily batch |
| `payflow-payment-events-dlq` | SQS (auto) | Manual review | Dead letter for failed processing |
| `payflow-webhook-delivery-dlq` | SQS (auto) | Manual review | Dead letter for permanently failed webhooks |

### 2.1 What Is a Dead Letter Queue (DLQ)?

```
NORMAL FLOW:
Message → Queue → Consumer reads → Process successfully → Message deleted ✅

FAILURE FLOW:
Message → Queue → Consumer reads → FAILS → Message goes back to queue
                                            → Consumer reads again → FAILS
                                            → (repeats 3 times)
                                            → After 3 failures, moves to DLQ

DLQ = "Graveyard for messages that couldn't be processed"
Operations team monitors DLQ and manually investigates.
```

---

## 3. Payment Events (Payment Service → Webhook Service)

### 3.1 Queue: `payflow-payment-events`

Every time a payment changes state, Payment Service publishes an event.
Webhook Service reads this and delivers webhooks to merchants.

### 3.2 Event: `payment.authorized`

**When published:** Bank approves authorization (field 39 = "00")

```json
{
  "event_id": "evt_a1b2c3d4e5f6g7h8",
  "event_type": "payment.authorized",
  "published_at": "2026-07-19T14:30:02Z",
  "merchant_id": "merch_xyz789",
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
      "type": "credit"
    },
    "auth_code": "A1B2C3",
    "risk_score": 25,
    "authorized_at": "2026-07-19T14:30:02Z"
  }
}
```

### 3.3 Event: `payment.captured`

**When published:** Merchant successfully captures (POST /capture returns 200)

```json
{
  "event_id": "evt_b2c3d4e5f6g7h8i9",
  "event_type": "payment.captured",
  "published_at": "2026-07-19T15:00:00Z",
  "merchant_id": "merch_xyz789",
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "captured_amount": 5000.00,
    "currency": "INR",
    "status": "captured",
    "method": "card",
    "card": {
      "last4": "1111",
      "network": "visa"
    },
    "captured_at": "2026-07-19T15:00:00Z"
  }
}
```

### 3.4 Event: `payment.failed`

**When published:** Bank declines, or timeout after reversal

```json
{
  "event_id": "evt_c3d4e5f6g7h8i9j0",
  "event_type": "payment.failed",
  "published_at": "2026-07-19T14:30:03Z",
  "merchant_id": "merch_xyz789",
  "data": {
    "payment_id": "pay_Mk8nO4xTs3",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "failed",
    "method": "card",
    "failure_code": "INSUFFICIENT_FUNDS",
    "failure_reason": "Card does not have sufficient balance",
    "bank_response_code": "51",
    "failed_at": "2026-07-19T14:30:03Z"
  }
}
```

### 3.5 Event: `payment.voided`

**When published:** Merchant voids an authorized payment

```json
{
  "event_id": "evt_d4e5f6g7h8i9j0k1",
  "event_type": "payment.voided",
  "published_at": "2026-07-19T16:00:00Z",
  "merchant_id": "merch_xyz789",
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "order_id": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "voided",
    "voided_at": "2026-07-19T16:00:00Z"
  }
}
```

### 3.6 Event: `refund.created`

**When published:** Refund initiated and processed

```json
{
  "event_id": "evt_e5f6g7h8i9j0k1l2",
  "event_type": "refund.created",
  "published_at": "2026-07-19T17:00:00Z",
  "merchant_id": "merch_xyz789",
  "data": {
    "refund_id": "rfnd_Qm4nP8wXv3",
    "payment_id": "pay_Hk7mN3xQp2",
    "amount": 2000.00,
    "currency": "INR",
    "status": "processed",
    "reason": "Customer returned product",
    "created_at": "2026-07-19T17:00:00Z"
  }
}
```

### 3.7 Event: `settlement.processed`

**When published:** Settlement Service completes daily batch

```json
{
  "event_id": "evt_f6g7h8i9j0k1l2m3",
  "event_type": "settlement.processed",
  "published_at": "2026-07-20T00:30:00Z",
  "merchant_id": "merch_xyz789",
  "data": {
    "settlement_id": "stl_Mn2kP9wQr5",
    "settlement_date": "2026-07-19",
    "gross_amount": 45000.00,
    "fee_amount": 900.00,
    "gst_on_fee": 162.00,
    "net_amount": 43938.00,
    "total_transactions": 15,
    "total_refunds": 2,
    "payout_utr": "HDFC2026072000456",
    "processed_at": "2026-07-20T00:30:00Z"
  }
}
```

---

## 4. Notification Events (→ Notification Service)

### 4.1 Queue: `payflow-notification`

These messages tell the Notification Service to send emails/SMS.

### 4.2 Payment Confirmation Email

```json
{
  "notification_id": "ntf_x1y2z3a4b5",
  "type": "EMAIL",
  "template": "PAYMENT_CONFIRMATION",
  "recipient": {
    "email": "buyer@gmail.com",
    "name": "Rajesh Kumar"
  },
  "data": {
    "payment_id": "pay_Hk7mN3xQp2",
    "merchant_name": "TechShop India",
    "amount": 5000.00,
    "currency": "INR",
    "payment_method": "Visa ending 1111",
    "date": "July 19, 2026 at 2:30 PM"
  },
  "published_at": "2026-07-19T14:30:05Z"
}
```

### 4.3 Refund Confirmation Email

```json
{
  "notification_id": "ntf_c5d6e7f8g9",
  "type": "EMAIL",
  "template": "REFUND_CONFIRMATION",
  "recipient": {
    "email": "buyer@gmail.com",
    "name": "Rajesh Kumar"
  },
  "data": {
    "refund_id": "rfnd_Qm4nP8wXv3",
    "payment_id": "pay_Hk7mN3xQp2",
    "refund_amount": 2000.00,
    "original_amount": 5000.00,
    "merchant_name": "TechShop India",
    "expected_days": "5-7 business days"
  },
  "published_at": "2026-07-19T17:00:05Z"
}
```

### 4.4 Fraud Alert (To Operations Team)

```json
{
  "notification_id": "ntf_h9i0j1k2l3",
  "type": "EMAIL",
  "template": "FRAUD_ALERT",
  "recipient": {
    "email": "ops-team@payflow.com",
    "name": "PayFlow Operations"
  },
  "data": {
    "payment_id": "pay_Fk2mX9pLq7",
    "merchant_id": "merch_xyz789",
    "risk_score": 85,
    "decision": "REVIEW",
    "reasons": [
      "High velocity: 6 transactions in 3 minutes",
      "New device detected",
      "Amount ₹75,000 exceeds usual ₹5,000 average"
    ],
    "card_last4": "4242",
    "amount": 75000.00,
    "timestamp": "2026-07-19T03:15:00Z"
  },
  "published_at": "2026-07-19T03:15:01Z"
}
```

### 4.5 Settlement Notification (To Merchant)

```json
{
  "notification_id": "ntf_m4n5o6p7q8",
  "type": "EMAIL",
  "template": "SETTLEMENT_PROCESSED",
  "recipient": {
    "email": "finance@techshop.in",
    "name": "TechShop Finance Team"
  },
  "data": {
    "settlement_id": "stl_Mn2kP9wQr5",
    "settlement_date": "2026-07-19",
    "net_amount": 43938.00,
    "total_transactions": 15,
    "payout_utr": "HDFC2026072000456",
    "expected_credit_date": "2026-07-20"
  },
  "published_at": "2026-07-20T00:35:00Z"
}
```

---

## 5. Webhook Delivery Format (PayFlow → Merchant's Server)

This is what the MERCHANT receives at their webhook URL.

### 5.1 HTTP Request Format

```http
POST https://merchant.com/webhooks/payflow HTTP/1.1
Content-Type: application/json
X-PayFlow-Signature: sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
X-PayFlow-Event: payment.captured
X-PayFlow-Timestamp: 1721401200
X-PayFlow-Delivery-Id: dlv_a1b2c3d4e5
User-Agent: PayFlow-Webhook/1.0

{
  "id": "evt_b2c3d4e5f6g7h8i9",
  "event": "payment.captured",
  "created_at": "2026-07-19T15:00:00Z",
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
    "captured_at": "2026-07-19T15:00:00Z"
  }
}
```

### 5.2 How Merchant Verifies the Webhook (Signature Check)

The merchant MUST verify the webhook is really from PayFlow (not a hacker):

```
STEP 1: Get the raw body (JSON string as received)
STEP 2: Get the timestamp from header: X-PayFlow-Timestamp
STEP 3: Create signed content: "{timestamp}.{body}"
STEP 4: Compute HMAC-SHA256 using their webhook_secret
STEP 5: Compare with X-PayFlow-Signature header
STEP 6: If match → it's from PayFlow ✅
         If no match → REJECT (someone is faking!) ❌

ALSO CHECK:
- Timestamp is within 5 minutes of current time
  (prevents replay attacks with old stolen webhooks)
```

**Merchant code example (Java):**
```java
String payload = timestamp + "." + requestBody;
String computed = HmacUtils.hmacSha256Hex(webhookSecret, payload);
String received = signatureHeader.replace("sha256=", "");

if (computed.equals(received)) {
    // Valid webhook from PayFlow ✅
    processEvent(requestBody);
    return ResponseEntity.ok().build(); // Return 200
} else {
    // FAKE webhook — reject ❌
    return ResponseEntity.status(401).build();
}
```

### 5.3 Expected Merchant Responses

| Response | Our Action |
|----------|-----------|
| 200, 201, 202, 204 | Mark as DELIVERED ✅ |
| 3xx (redirect) | Do NOT follow redirect, mark as FAILED |
| 4xx (client error) | Mark as FAILED (permanent — don't retry 400s) |
| 5xx (server error) | Schedule RETRY |
| Timeout (>10 seconds) | Schedule RETRY |
| Connection refused | Schedule RETRY |

### 5.4 Retry Schedule

```
Attempt 1: Immediately (when event happens)
         └── Failed? Wait 5 minutes...

Attempt 2: After 5 minutes
         └── Failed? Wait 30 minutes...

Attempt 3: After 30 minutes
         └── Failed? Wait 2 hours...

Attempt 4: After 2 hours
         └── Failed? Wait 24 hours...

Attempt 5: After 24 hours
         └── Failed? → Move to Dead Letter Queue
             → Alert operations team
             → Merchant can manually retry from dashboard
```

---

## 6. Internal Domain Events (Within Same Service)

These are Spring ApplicationEvents that stay within one service (no SQS needed).

### 6.1 Payment Service Internal Events

```java
// Published within payment-service after state change
public class PaymentStateChangedEvent {
    private String paymentId;
    private PaymentState fromState;
    private PaymentState toState;
    private String trigger;      // "MERCHANT_CAPTURE", "BANK_APPROVED", "SCHEDULER_EXPIRE"
    private Instant timestamp;
}

// Listener within same service (saves audit record):
@EventListener
public void onPaymentStateChanged(PaymentStateChangedEvent event) {
    // Save to payment_state_history table
    stateHistoryRepository.save(new StateHistory(
        event.getPaymentId(),
        event.getFromState(),
        event.getToState(),
        event.getTrigger(),
        event.getTimestamp()
    ));
}
```

---

## 7. Message Flow Summary

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE MESSAGE FLOW MAP                              │
│                                                                           │
│  Payment Service                                                          │
│       │                                                                   │
│       ├──── SQS: payflow-payment-events ──────► Webhook Service          │
│       │     (payment.authorized, captured,        (builds & delivers      │
│       │      failed, voided, refund.created)       webhooks to merchant)  │
│       │                                                                   │
│       ├──── SQS: payflow-notification ────────► Notification Service     │
│       │     (send email to customer,              (calls AWS SNS to       │
│       │      send SMS, send fraud alert)           send email/SMS)        │
│       │                                                                   │
│       └──── Internal Event ───────────────────► State History Listener   │
│             (PaymentStateChangedEvent)            (saves audit to DB)     │
│                                                                           │
│  Settlement Service                                                       │
│       │                                                                   │
│       ├──── SQS: payflow-payment-events ──────► Webhook Service          │
│       │     (settlement.processed)                (webhook to merchant)   │
│       │                                                                   │
│       └──── SQS: payflow-notification ────────► Notification Service     │
│             (settlement email to merchant)        (email via SNS)         │
│                                                                           │
│  Webhook Service (self-retry)                                            │
│       │                                                                   │
│       └──── SQS: payflow-webhook-delivery ────► Webhook Service          │
│             (retry failed webhook delivery)       (tries again)          │
│                                                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 8. SQS Configuration Details

| Queue | Visibility Timeout | Max Receive Count | DLQ |
|-------|-------------------|-------------------|-----|
| payflow-payment-events | 60 seconds | 3 | payflow-payment-events-dlq |
| payflow-notification | 30 seconds | 3 | (shared dlq) |
| payflow-webhook-delivery | 300 seconds (5 min) | 5 | payflow-webhook-delivery-dlq |
| payflow-settlement-trigger | 60 seconds | 1 | None |

**What is Visibility Timeout?**
```
When a consumer reads a message from SQS:
├── Message becomes INVISIBLE to other consumers
├── Consumer has [visibility timeout] seconds to process and DELETE it
├── If consumer crashes (doesn't delete in time):
│   └── Message becomes visible again → another consumer picks it up
└── This prevents duplicate processing while allowing retry on failure
```

---

## 9. Interview Questions This Document Answers

1. **"How do services communicate asynchronously?"**
   → SQS queues. Payment Service publishes events, Webhook/Notification services consume.

2. **"What events does your system produce?"**
   → payment.authorized, payment.captured, payment.failed, payment.voided, refund.created, settlement.processed

3. **"How do you ensure webhook delivery?"**
   → 5 retries with exponential backoff (immediate, 5m, 30m, 2h, 24h). DLQ for permanent failures.

4. **"How do merchants verify webhooks are authentic?"**
   → HMAC-SHA256 signature. We sign with merchant's secret, they verify with same secret.

5. **"What happens if webhook consumer crashes mid-processing?"**
   → SQS visibility timeout. Message becomes visible again after 60s, another consumer processes it.

6. **"Why SQS instead of direct HTTP calls for webhooks?"**
   → Decoupling. Payment Service returns fast to customer. Webhook delivery happens async with its own retry logic.

---

## Phase 2 Complete! 🎉

All 6 parts of Phase 2 (System Design) are done:

| Part | Document | Content |
|------|----------|---------|
| 1 | High-Level Design | Architecture diagrams, data flows, scaling |
| 2 | Low-Level Design | Class diagrams, patterns, interfaces |
| 3 | Database Schema | Full SQL DDL, DynamoDB tables, indexes |
| 4 | API Specification | Every REST endpoint with examples |
| 5 | ISO 8583 Spec | Message formats, field details, simulator rules |
| 6 | Event Contracts | SQS messages, webhooks, notification payloads |

---

## Next Step

→ Move to **Phase 3: Infrastructure Services**
→ Start with **`phase3-part1-parent-pom-and-common-lib.md`**

**In Phase 3, we start writing actual Java code!**
- Create the multi-module Maven project (parent pom.xml)
- Build the common-lib (shared DTOs, exceptions)
- Set up Eureka Server
- Set up Config Server
- Set up API Gateway
- Create Docker Compose for infrastructure
