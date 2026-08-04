# Hands-On Guide — Phase 6 Part 6: Refund

## Goal

By the end of Part 6, you will have:
- RefundService with createRefund() logic
- POST /v1/payments/{id}/refund endpoint (full + partial refund)
- Refund amount validation (can't refund more than captured - already refunded)
- Refund records saved to refunds table
- Git commit

## Prerequisites

- Part 5 completed (capture working, at least one payment in CAPTURED state)

---

## How Refunds Work

```
SCENARIO: Customer bought headphones (₹5000), wants to return them.

1. Merchant calls: POST /v1/payments/pay_xxx/refund { amount: 5000, reason: "Customer return" }
2. We validate:
   ├── Payment exists? ✓
   ├── Payment is CAPTURED? ✓ (can only refund captured payments)
   ├── Refund amount ≤ (captured_amount - already_refunded_amount)? ✓
   └── All valid
3. Create refund record (rfnd_Qm4nP8wXv3)
4. Update payment: refunded_amount += 5000
5. If fully refunded (refunded_amount == captured_amount) → payment status = REFUNDED
6. If partially refunded → payment stays CAPTURED (with refunded_amount tracking)
7. In Phase 7: Send ISO 8583 0400 reversal to bank (actual money return)
8. Return refund details to merchant

PARTIAL REFUND EXAMPLE:
├── Payment: ₹10,000 captured
├── Refund 1: ₹3,000 (headphones returned)
│   └── Payment: captured=10000, refunded=3000, status still CAPTURED
├── Refund 2: ₹2,000 (charger returned)
│   └── Payment: captured=10000, refunded=5000, status still CAPTURED
├── Refund 3: ₹5,000 (remaining items)
│   └── Payment: captured=10000, refunded=10000, status → REFUNDED
└── Cannot refund more: 10000 - 10000 = ₹0 available

TIMELINE (customer perspective):
├── Day 0: Paid ₹5000 (deducted from card)
├── Day 3: Returned product, merchant initiates refund
├── Day 3: Refund initiated (status: INITIATED)
├── Day 3: Refund processed with bank (status: PROCESSED)
└── Day 5-7: Money appears back in customer's account (bank processing time)
```

---

## Step 6.1: Create RefundRequest DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/RefundRequest.java`

```java
package com.payflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "1.00", message = "Refund amount must be at least ₹1.00")
    private BigDecimal amount;
    // How much to refund
    // Full refund: same as captured amount
    // Partial refund: less than captured amount

    private String reason;
    // Optional: "Customer returned product", "Duplicate charge", "Order cancelled"
    // Stored for audit trail and merchant dashboard display
}
```

---

## Step 6.2: Create RefundResponse DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/RefundResponse.java`

```java
package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private String refundId;       // rfnd_Qm4nP8wXv3
    private String paymentId;      // pay_Hk7mN3xQp2
    private BigDecimal amount;     // 2000.00
    private String status;         // initiated, processed, failed
    private String reason;         // "Customer returned product"
    private String rrn;            // Bank reference for refund
    private Instant createdAt;
    private Instant processedAt;
}
```

---

## Step 6.3: Create Refund Entity

**Create file:** `payment-service/src/main/java/com/payflow/payment/model/Refund.java`

```java
package com.payflow.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds", schema = "payment")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Refund {

    @Id
    @Column(length = 50)
    private String id;                // rfnd_Qm4nP8wXv3

    @Column(name = "payment_id", nullable = false, length = 50)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.INITIATED;

    @Column(length = 500)
    private String reason;

    @Column(length = 20)
    private String rrn;               // Bank reference for refund

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum RefundStatus {
        INITIATED,   // Refund created, pending bank processing
        PROCESSED,   // Bank confirmed refund
        FAILED       // Bank rejected refund
    }
}
```

---

## Step 6.4: Add Refund Logic to PaymentProcessorService

**Add to:** `PaymentProcessorService.java`

```java
    /**
     * Refund a captured payment (full or partial).
     */
    @Transactional
    public RefundResponse refundPayment(String paymentId, RefundRequest request) {
        Payment payment = findPaymentOrThrow(paymentId);

        // Must be CAPTURED to refund (can't refund authorized-only or voided)
        if (payment.getStatus() != PaymentStatus.CAPTURED 
                && payment.getStatus() != PaymentStatus.SETTLED) {
            throw new InvalidStateTransitionException(payment.getStatus().name(), "refund");
        }

        // Calculate available refund amount
        BigDecimal availableForRefund = payment.getCapturedAmount()
                .subtract(payment.getRefundedAmount());
        // available = captured - already_refunded
        // Example: captured ₹5000, already refunded ₹2000 → available ₹3000

        // Validate refund amount
        if (request.getAmount().compareTo(availableForRefund) > 0) {
            throw new PayflowException("REFUND_EXCEEDS_AVAILABLE",
                    "Refund amount (₹" + request.getAmount() 
                    + ") exceeds available (₹" + availableForRefund 
                    + "). Captured: ₹" + payment.getCapturedAmount() 
                    + ", Already refunded: ₹" + payment.getRefundedAmount(),
                    HttpStatus.BAD_REQUEST);
        }

        // Create refund record
        Refund refund = Refund.builder()
                .id(IdGenerator.refundId())         // "rfnd_Qm4nP8wXv3"
                .paymentId(paymentId)
                .merchantId(payment.getMerchantId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(Refund.RefundStatus.PROCESSED)  // Simulated instant processing
                .processedAt(Instant.now())
                .build();

        refundRepository.save(refund);

        // Update payment's refunded amount
        payment.setRefundedAmount(payment.getRefundedAmount().add(request.getAmount()));

        // If fully refunded, change payment status
        if (payment.getRefundedAmount().compareTo(payment.getCapturedAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }

        paymentRepository.save(payment);

        log.info("Refund {} created: ₹{} for payment {} (total refunded: ₹{})", 
                refund.getId(), refund.getAmount(), paymentId, payment.getRefundedAmount());

        return RefundResponse.builder()
                .refundId(refund.getId())
                .paymentId(paymentId)
                .amount(refund.getAmount())
                .status(refund.getStatus().name().toLowerCase())
                .reason(refund.getReason())
                .createdAt(refund.getCreatedAt())
                .processedAt(refund.getProcessedAt())
                .build();
    }
```

**Add to class fields:**
```java
    private final RefundRepository refundRepository;
```

**Add RefundRepository:**

**Create file:** `payment-service/src/main/java/com/payflow/payment/repository/RefundRepository.java`

```java
package com.payflow.payment.repository;

import com.payflow.payment.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByPaymentId(String paymentId);
    List<Refund> findByMerchantId(String merchantId);
}
```

---

## Step 6.5: Add Refund Endpoint to PaymentController

**Add to** `PaymentController.java`:

```java
    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a captured payment (full or partial)")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody RefundRequest request) {
        RefundResponse response = paymentProcessor.refundPayment(paymentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
```

---

## Step 6.6: Verify with curl

### Full refund:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_CAPTURED_ID/refund ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00,\"reason\":\"Customer returned product\"}"
```

**Expected (201):**
```json
{
  "success": true,
  "data": {
    "refundId": "rfnd_Qm4nP8wXv3",
    "paymentId": "pay_CAPTURED_ID",
    "amount": 5000.00,
    "status": "processed",
    "reason": "Customer returned product"
  }
}
```

### Try to refund more than available:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_CAPTURED_ID/refund ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":1000.00,\"reason\":\"Extra refund\"}"
```

**Expected (400):** `"Refund amount (₹1000.00) exceeds available (₹0.00)"`

---

## Step 6.7: Git Commit

```cmd
git add payment-service/
git commit -m "Phase 6 Part 6: Refund endpoint - full/partial refund with amount validation"
```

---

## What We Built

| Feature | Logic |
|---------|-------|
| Full refund | Refund entire captured amount → status: REFUNDED |
| Partial refund | Refund part → status stays CAPTURED, refunded_amount increases |
| Validation | Can't refund more than (captured - already_refunded) |
| State check | Only CAPTURED/SETTLED payments can be refunded |
| Refund record | Saved to refunds table with reason and bank reference |

---

## Next Step

→ Continue to **Phase 6 Part 7: Idempotency**
