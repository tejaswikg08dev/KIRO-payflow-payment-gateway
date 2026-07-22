# Hands-On Guide — Phase 6 Part 5: Capture & Void

## Goal

By the end of Part 5, you will have:
- POST /v1/payments/{id}/capture working (full + partial capture)
- POST /v1/payments/{id}/void working
- State machine enforcing valid transitions
- Error responses for invalid operations
- Git commit

## Prerequisites

- Part 4 completed (payments can be authorized)
- At least one payment in AUTHORIZED state

---

## Capture vs Void — When to Use Each

```
CAPTURE: "Yes, charge the customer! Ship the product!"
├── Used when: Merchant confirms the order (stock available, ready to ship)
├── Effect: Money ACTUALLY moves from customer's bank to gateway holding
├── After capture: Can refund (return money) but can't void
├── Partial capture: Authorized ₹500, capture only ₹320 (Uber ride example)
└── Full capture: Authorized ₹5000, capture ₹5000

VOID: "Never mind, cancel the hold"
├── Used when: Merchant cancels before shipping (out of stock, customer changed mind)
├── Effect: Hold released on customer's card (money was never deducted)
├── After void: Payment is permanently cancelled
└── Customer sees: "Pending charge" disappears from their statement

REAL-WORLD EXAMPLES:
├── Amazon: Authorize on order → Capture when shipped → Void if out of stock
├── Hotel: Authorize on booking → Capture on check-in → Void on cancellation
├── Uber: Authorize ₹500 estimate → Capture ₹320 actual fare (partial)
└── Restaurant: Authorize meal cost → Capture meal + tip (can't exceed auth)
```

---

## Step 5.1: Capture Logic (Already in PaymentProcessorService)

The capture logic is in `PaymentProcessorService.capturePayment()`:

```java
@Transactional
public PaymentResponse capturePayment(String paymentId, CaptureRequest request) {
    Payment payment = findPaymentOrThrow(paymentId);

    // STATE MACHINE: Only AUTHORIZED → CAPTURED is allowed
    stateMachine.validateTransition(
            payment.getStatus(), PaymentStatus.CAPTURED, "capture");
    // If payment is VOIDED, FAILED, or CREATED → throws InvalidStateTransitionException

    // Determine capture amount
    BigDecimal captureAmount = (request != null && request.getAmount() != null)
            ? request.getAmount()       // Partial capture (merchant specifies amount)
            : payment.getAmount();      // Full capture (capture everything)

    // VALIDATION: Can't capture MORE than was authorized
    if (captureAmount.compareTo(payment.getAmount()) > 0) {
        throw new PayflowException("AMOUNT_EXCEEDS_AUTHORIZED",
                "Capture amount (₹" + captureAmount + ") exceeds authorized (₹" + payment.getAmount() + ")",
                HttpStatus.BAD_REQUEST);
    }

    // Perform the capture
    payment.setStatus(PaymentStatus.CAPTURED);
    payment.setCapturedAmount(captureAmount);
    payment.setCapturedAt(Instant.now());
    paymentRepository.save(payment);

    // TODO: In Phase 7, send ISO 8583 0200 (Financial Request) to bank
    // TODO: In Phase 9, publish "payment.captured" event to SQS

    log.info("Payment {} captured: ₹{} (of ₹{} authorized)",
            paymentId, captureAmount, payment.getAmount());
    return toResponse(payment);
}
```

---

## Step 5.2: Void Logic (Already in PaymentProcessorService)

```java
@Transactional
public PaymentResponse voidPayment(String paymentId) {
    Payment payment = findPaymentOrThrow(paymentId);

    // STATE MACHINE: Only AUTHORIZED → VOIDED is allowed
    stateMachine.validateTransition(
            payment.getStatus(), PaymentStatus.VOIDED, "void");
    // Can't void a CAPTURED payment (must refund instead)
    // Can't void a FAILED payment (nothing to void)

    payment.setStatus(PaymentStatus.VOIDED);
    paymentRepository.save(payment);

    // TODO: In Phase 7, send ISO 8583 0400 (Reversal) to bank
    // TODO: In Phase 9, publish "payment.voided" event to SQS

    log.info("Payment {} voided (authorization cancelled)", paymentId);
    return toResponse(payment);
}
```

---

## Step 5.3: CaptureRequest DTO

**File already exists:** `payment-service/src/main/java/com/payflow/payment/dto/CaptureRequest.java`

```java
package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptureRequest {
    private BigDecimal amount;
    // Optional: If null → full capture (capture entire authorized amount)
    // If specified → partial capture (e.g., ₹320 out of ₹500)
    //
    // Partial capture rule: amount must be ≤ authorized amount
    // After partial capture: remaining hold is released automatically
}
```

---

## Step 5.4: Verify with curl

### Full Capture:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_Hk7mN3xQp2/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```

**Expected (200):**
```json
{
  "success": true,
  "data": {
    "paymentId": "pay_Hk7mN3xQp2",
    "status": "captured",
    "capturedAmount": 5000.00,
    "capturedAt": "2026-07-20T11:30:00Z"
  }
}
```

### Partial Capture (new payment):
Authorize ₹500, then capture only ₹320:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_NEW_ID/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":320.00}"
```

### Try to Capture Again (should fail — already captured):
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_Hk7mN3xQp2/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```

**Expected (400):**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE_TRANSITION",
    "message": "Cannot capture. Current status: 'CAPTURED'. This action is not allowed in this state."
  }
}
```

### Void an Authorized Payment (create new auth first):
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_AUTHORIZED_ID/void
```

**Expected (200):**
```json
{
  "success": true,
  "data": {
    "paymentId": "pay_AUTHORIZED_ID",
    "status": "voided"
  }
}
```

### Try to Capture a Voided Payment (should fail):
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_VOIDED_ID/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```

**Expected (400):** `"Cannot capture. Current status: 'VOIDED'"`

---

## Step 5.5: Git Commit

```cmd
git add payment-service/
git commit -m "Phase 6 Part 5: Capture (full + partial) and Void endpoints with state validation"
```

---

## What We Built

| Endpoint | What It Does | State Transition |
|----------|-------------|-----------------|
| `POST /payments/{id}/capture` | Charge the customer (money moves) | AUTHORIZED → CAPTURED |
| `POST /payments/{id}/capture` (partial) | Charge less than authorized | AUTHORIZED → CAPTURED (partial) |
| `POST /payments/{id}/void` | Cancel auth (release hold) | AUTHORIZED → VOIDED |

| Error Case | Result |
|-----------|--------|
| Capture a voided payment | 400: Invalid state transition |
| Capture more than authorized | 400: Amount exceeds authorized |
| Void a captured payment | 400: Invalid state transition |
| Capture a failed payment | 400: Invalid state transition |

---

## Interview Notes

**Q: "What's the difference between void and refund?"**
> "Void cancels an authorization BEFORE money moves — the hold on the customer's card is released (no money ever left their account). Refund returns money AFTER it was captured — money goes back from our holding to the customer. Void is instant and free. Refund takes 5-7 business days and may incur processing fees."

**Q: "What is partial capture?"**
> "When you authorize ₹500 but only charge ₹320. Used by ride-sharing apps (authorize estimated fare, capture actual fare). The remaining ₹180 hold is released automatically after capture. Our system validates that capture amount ≤ authorized amount."

---

## Next Step

→ Continue to **Phase 6 Part 6: Refund**
