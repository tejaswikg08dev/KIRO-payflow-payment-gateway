# Hands-On Guide — Phase 6 Part 4: Payment Authorization

## Goal

By the end of Part 4, you will have:
- PaymentProcessorService that handles the full authorization flow
- Card payment: validate → fraud check → route to bank → approve/decline
- UPI payment: validate VPA → simulate collect → approve
- POST /v1/payments endpoint working with curl
- Understanding of what happens in those 2-3 seconds when customer clicks "Pay"
- Git commit

## Prerequisites

- Part 3 completed (orders can be created, order_id available)
- Redis running (docker compose)
- payment-service starts on port 8083

---

## What Happens When Customer Clicks "Pay" (The Full Flow)

```
CUSTOMER CLICKS "PAY ₹5000" ON CHECKOUT PAGE:

Second 0.0: Browser sends POST /v1/payments to API Gateway
Second 0.01: Gateway validates API key, adds X-Merchant-Id, forwards to payment-service
Second 0.02: Payment Service receives request
Second 0.03: ┌─ CHECK IDEMPOTENCY (Redis)
             │  Is this a duplicate? → If yes, return cached response
             └─ New request → continue
Second 0.05: ┌─ VALIDATE ORDER
             │  Does order exist? Is it CREATED (not expired/paid)?
             │  Amount matches? Merchant matches?
             └─ All valid → continue
Second 0.1:  ┌─ FRAUD CHECK
             │  Score this transaction (rules + ML model)
             │  Score: 25 (low risk) → APPROVE
             │  Score: 95 (high risk) → AUTO-DECLINE (skip bank call)
             └─ Score OK → continue to bank
Second 0.15: ┌─ ROUTE TO BANK
             │  Select best bank route (HDFC, ICICI, Axis)
             │  Build ISO 8583 authorization request (0100)
             │  Send via TCP to bank simulator
             └─ Wait for response...
Second 0.35: ┌─ BANK RESPONDS (ISO 8583 0110)
             │  Response code "00" → APPROVED! Auth code: A1B2C3
             │  Response code "51" → DECLINED (insufficient funds)
             └─ Parse response
Second 0.4:  ┌─ SAVE TO DATABASE
             │  Status: AUTHORIZED (or FAILED)
             │  Auth code, RRN, risk score saved
             └─ Order marked as PAID (if authorized)
Second 0.45: ┌─ PUBLISH EVENT TO SQS
             │  "payment.authorized" event → webhook-service picks up later
             └─ Async (doesn't block response)
Second 0.5:  ┌─ RETURN RESPONSE TO CUSTOMER
             │  { status: "authorized", auth_code: "A1B2C3", payment_id: "pay_xxx" }
             └─ Customer sees "Payment Successful!" ✅

TOTAL TIME: ~500ms (well within our 500ms target)
```

---

## Step 4.1: Create PaymentRequest DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/PaymentRequest.java`

```java
package com.payflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for POST /v1/payments.
 * Contains the order reference + payment method details.
 * 
 * Example (card payment):
 * {
 *   "orderId": "ord_LkR3d9xF2m",
 *   "amount": 5000.00,
 *   "method": "card",
 *   "card": {
 *     "number": "4111111111111111",
 *     "expiryMonth": 12,
 *     "expiryYear": 2028,
 *     "cvv": "123",
 *     "holderName": "RAJESH KUMAR"
 *   }
 * }
 * 
 * Example (UPI payment):
 * {
 *   "orderId": "ord_LkR3d9xF2m",
 *   "amount": 5000.00,
 *   "method": "upi",
 *   "upi": { "vpa": "rajesh@okicici" }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;
    // Must reference an existing order (created in Part 3)

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least ₹1.00")
    private BigDecimal amount;
    // Must match the order amount (or be less for partial payments)

    private String currency;
    // Defaults to "INR" if not provided

    @NotBlank(message = "Payment method is required (card, upi, netbanking)")
    private String method;
    // "card", "upi", "netbanking", "wallet"

    private CardDetails card;
    // Required when method = "card"

    private UpiDetails upi;
    // Required when method = "upi"

    /**
     * Card payment details.
     * NOTE: We NEVER store the full card number!
     * It passes through our system to the bank and is immediately discarded.
     * We only keep last4 digits for display.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetails {
        private String number;
        // Full 16-digit card number: "4111111111111111"
        // Used ONLY for this transaction, then discarded (PCI compliance)
        
        private int expiryMonth;   // 1-12
        private int expiryYear;    // 2024-2040
        private String cvv;        // 3 digits: "123"
        private String holderName; // Name on card: "RAJESH KUMAR"
    }

    /**
     * UPI payment details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpiDetails {
        private String vpa;
        // Virtual Payment Address: "rajesh@okicici", "9876543210@ybl"
    }
}
```

---

## Step 4.2: Create PaymentResponse DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/PaymentResponse.java`

```java
package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response returned after payment operations (authorize, capture, void, get).
 * 
 * Contains everything the merchant needs to know about this payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;      // pay_Hk7mN3xQp2
    private String orderId;        // ord_LkR3d9xF2m
    private BigDecimal amount;     // 5000.00
    private String currency;       // INR
    private String status;         // authorized, captured, failed, voided, refunded

    private String method;         // card, upi, netbanking
    private String cardLast4;      // 1111 (masked — never full number!)
    private String cardNetwork;    // visa, mastercard, rupay
    private String upiVpa;         // rajesh@okicici

    private String authCode;       // A1B2C3 (from bank — proof of approval)
    private String rrn;            // 987654321012 (bank's reference number)

    private Integer riskScore;     // 0-100 from fraud engine
    private String routeUsed;      // HDFC_ACQ_01 (which bank processed it)

    private BigDecimal capturedAmount;  // How much captured so far
    private BigDecimal refundedAmount;  // How much refunded so far

    private String failureCode;    // INSUFFICIENT_FUNDS (if declined)
    private String failureReason;  // "Card does not have sufficient balance"

    private Instant authorizedAt;  // When bank approved
    private Instant capturedAt;    // When merchant captured
    private Instant createdAt;     // When payment was created
}
```

---

## Step 4.3: Create PaymentProcessorService (Core Logic)

This is the HEART of the payment system. Every payment goes through this class.

**Create file:** `payment-service/src/main/java/com/payflow/payment/service/PaymentProcessorService.java`

```java
package com.payflow.payment.service;

import com.payflow.common.constant.PaymentMethod;
import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import com.payflow.common.exception.PayflowException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.payment.dto.CaptureRequest;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.model.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.statemachine.PaymentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateMachine stateMachine;
    private final IdempotencyService idempotencyService;
    private final OrderService orderService;
    // In Phase 7, we'll also inject RoutingServiceClient (Feign)

    // ========================================================================
    // AUTHORIZE PAYMENT — The main entry point
    // ========================================================================

    /**
     * Process a new payment authorization.
     * 
     * This is what runs when customer clicks "Pay":
     * 1. Check idempotency (prevent duplicate)
     * 2. Validate input + order
     * 3. Run fraud detection
     * 4. Route to bank (ISO 8583)
     * 5. Save result
     * 6. Return response
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, 
                                           String idempotencyKey, 
                                           String merchantId) {

        // ===== STEP 1: Idempotency Check =====
        // If this exact same request was already processed, return cached result
        if (idempotencyKey != null) {
            PaymentResponse cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached != null) {
                log.info("Idempotency hit: returning cached response for key={}", idempotencyKey);
                return cached;
                // Customer's retry gets the SAME response — no double charge!
            }
        }

        // ===== STEP 2: Parse and Validate =====
        PaymentMethod method = parsePaymentMethod(request.getMethod());
        String resolvedMerchantId = (merchantId != null) ? merchantId : "merch_default";

        // ===== STEP 3: Create Payment Record (status: PROCESSING) =====
        Payment payment = Payment.builder()
                .id(IdGenerator.paymentId())           // "pay_Hk7mN3xQp2"
                .orderId(request.getOrderId())
                .merchantId(resolvedMerchantId)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .status(PaymentStatus.PROCESSING)      // Processing = talking to bank
                .paymentMethod(method)
                .idempotencyKey(idempotencyKey)
                .build();

        // Set payment-method-specific fields
        setMethodSpecificFields(payment, request);

        // ===== STEP 4: Fraud Detection =====
        int riskScore = runFraudCheck(payment);
        payment.setRiskScore(riskScore);

        if (riskScore > 90) {
            // HIGH RISK — Auto-decline without calling bank
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode("FRAUD_DETECTED");
            payment.setFailureReason(
                "Transaction blocked by fraud detection (risk score: " + riskScore + ")");
            log.warn("Payment {} auto-declined by fraud engine (score: {})", 
                    payment.getId(), riskScore);
        } else {
            // ===== STEP 5: Route to Bank (simulated) =====
            // In Phase 7, this calls routing-service via Feign → ISO 8583 → bank
            // For now, we simulate the bank response
            simulateBankAuthorization(payment);
        }

        // ===== STEP 6: Save to Database =====
        paymentRepository.save(payment);

        // Mark order as paid (if authorized)
        if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
            orderService.markAsPaid(payment.getOrderId());
        }

        log.info("Payment {} completed: status={}, method={}, amount=₹{}", 
                payment.getId(), payment.getStatus(), method, payment.getAmount());

        // ===== STEP 7: Build Response =====
        PaymentResponse response = toResponse(payment);

        // ===== STEP 8: Cache for Idempotency =====
        if (idempotencyKey != null) {
            idempotencyService.cacheResponse(idempotencyKey, response);
        }

        return response;
    }

    // ========================================================================
    // CAPTURE — Merchant confirms, money actually moves
    // ========================================================================

    @Transactional
    public PaymentResponse capturePayment(String paymentId, CaptureRequest request) {
        Payment payment = findPaymentOrThrow(paymentId);

        // State machine check: must be AUTHORIZED to capture
        stateMachine.validateTransition(
                payment.getStatus(), PaymentStatus.CAPTURED, "capture");

        // Determine capture amount
        BigDecimal captureAmount = (request != null && request.getAmount() != null)
                ? request.getAmount()
                : payment.getAmount(); // Full capture if no amount specified

        // Validate: can't capture more than authorized
        if (captureAmount.compareTo(payment.getAmount()) > 0) {
            throw new PayflowException("AMOUNT_EXCEEDS_AUTHORIZED",
                    "Capture amount (₹" + captureAmount + ") exceeds authorized amount (₹" 
                    + payment.getAmount() + ")",
                    HttpStatus.BAD_REQUEST);
        }

        // Perform capture
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(captureAmount);
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        log.info("Payment {} captured: ₹{}", paymentId, captureAmount);
        return toResponse(payment);
    }

    // ========================================================================
    // VOID — Cancel authorization (release hold on customer's card)
    // ========================================================================

    @Transactional
    public PaymentResponse voidPayment(String paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);

        // State machine check: must be AUTHORIZED to void
        stateMachine.validateTransition(
                payment.getStatus(), PaymentStatus.VOIDED, "void");

        payment.setStatus(PaymentStatus.VOIDED);
        paymentRepository.save(payment);

        log.info("Payment {} voided (authorization cancelled)", paymentId);
        return toResponse(payment);
    }

    // ========================================================================
    // GET PAYMENT — Retrieve payment details
    // ========================================================================

    public PaymentResponse getPayment(String paymentId) {
        return toResponse(findPaymentOrThrow(paymentId));
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private Payment findPaymentOrThrow(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }

    private PaymentMethod parsePaymentMethod(String method) {
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PayflowException("INVALID_PAYMENT_METHOD",
                    "Unsupported payment method: '" + method 
                    + "'. Supported: card, upi, netbanking, wallet",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void setMethodSpecificFields(Payment payment, PaymentRequest request) {
        if (payment.getPaymentMethod() == PaymentMethod.CARD && request.getCard() != null) {
            String cardNum = request.getCard().getNumber();
            payment.setCardLast4(cardNum.substring(cardNum.length() - 4));
            // Store ONLY last 4 digits! Full number is NEVER stored (PCI compliance)
            payment.setCardNetwork(detectCardNetwork(cardNum));
        } else if (payment.getPaymentMethod() == PaymentMethod.UPI && request.getUpi() != null) {
            payment.setUpiVpa(request.getUpi().getVpa());
        }
    }

    /**
     * Simulate fraud check. Returns risk score 0-100.
     * In Phase 10, this becomes a real rule engine + ML model.
     */
    private int runFraudCheck(Payment payment) {
        int score = (int) (Math.random() * 35); // Base: 0-35 (most transactions are low risk)
        // Add points for risk indicators:
        if (payment.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            score += 25; // Large amount = higher risk
        }
        if (payment.getAmount().compareTo(new BigDecimal("100000")) > 0) {
            score += 20; // Very large = even higher
        }
        return Math.min(score, 100);
    }

    /**
     * Simulate bank authorization response.
     * In Phase 7, this is replaced by Feign call to routing-service → ISO 8583.
     */
    private void simulateBankAuthorization(Payment payment) {
        // Simulate: 95% approval rate (real-world is ~85-95%)
        boolean approved = Math.random() < 0.95;

        if (approved) {
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthCode(generateAuthCode());
            payment.setRrn(generateRrn());
            payment.setRouteId("HDFC_ACQ_01"); // Simulated route
            payment.setAuthorizedAt(Instant.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode("BANK_DECLINED");
            payment.setFailureReason("Transaction declined by issuing bank");
        }
    }

    private String detectCardNetwork(String cardNumber) {
        if (cardNumber.startsWith("4")) return "visa";
        if (cardNumber.startsWith("5")) return "mastercard";
        if (cardNumber.startsWith("6")) return "rupay";
        if (cardNumber.startsWith("3")) return "amex";
        return "unknown";
    }

    private String generateAuthCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString(); // Example: "A1B2C3"
    }

    private String generateRrn() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString(); // Example: "987654321012"
    }

    /**
     * Convert Payment entity to response DTO.
     * This is what the API returns to the merchant.
     */
    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus().name().toLowerCase())
                .method(p.getPaymentMethod().name().toLowerCase())
                .cardLast4(p.getCardLast4())
                .cardNetwork(p.getCardNetwork())
                .upiVpa(p.getUpiVpa())
                .authCode(p.getAuthCode())
                .rrn(p.getRrn())
                .riskScore(p.getRiskScore())
                .routeUsed(p.getRouteId())
                .capturedAmount(p.getCapturedAmount())
                .refundedAmount(p.getRefundedAmount())
                .failureCode(p.getFailureCode())
                .failureReason(p.getFailureReason())
                .authorizedAt(p.getAuthorizedAt())
                .capturedAt(p.getCapturedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
```

---

## Step 4.4: Verify with curl

### Authorize a card payment:
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: test_idem_001" ^
  -H "X-Merchant-Id: merch_test123" ^
  -d "{\"orderId\":\"ord_LkR3d9xF2m\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\",\"holderName\":\"RAJESH KUMAR\"}}"
```

**Expected (201 — Authorized):**
```json
{
  "success": true,
  "data": {
    "paymentId": "pay_Hk7mN3xQp2",
    "orderId": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "status": "authorized",
    "method": "card",
    "cardLast4": "1111",
    "cardNetwork": "visa",
    "authCode": "A1B2C3",
    "rrn": "987654321012",
    "riskScore": 18,
    "routeUsed": "HDFC_ACQ_01",
    "authorizedAt": "2026-07-20T11:00:02Z"
  }
}
```

### Test idempotency (same key = same response):
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: test_idem_001" ^
  -H "X-Merchant-Id: merch_test123" ^
  -d "{\"orderId\":\"ord_LkR3d9xF2m\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\",\"holderName\":\"RAJESH KUMAR\"}}"
```
**Expected:** SAME response as above (cached). No new payment created!

### Authorize UPI payment:
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: test_idem_002" ^
  -d "{\"orderId\":\"ord_ANOTHER_ID\",\"amount\":1200.00,\"method\":\"upi\",\"upi\":{\"vpa\":\"rajesh@okicici\"}}"
```

---

## Step 4.5: Git Commit

```cmd
git add payment-service/src/main/java/com/payflow/payment/
git commit -m "Phase 6 Part 4: Payment authorization - full flow (fraud check, bank sim, idempotency)"
```

---

## What We Built

| Component | What It Does |
|-----------|-------------|
| `PaymentRequest.java` | Input DTO with card/UPI details |
| `PaymentResponse.java` | Output DTO with all payment info |
| `PaymentProcessorService.processPayment()` | Full auth flow: idempotency → fraud → bank → save |
| `PaymentProcessorService.capturePayment()` | Capture authorized payment |
| `PaymentProcessorService.voidPayment()` | Cancel authorization |
| Fraud check (simulated) | Scores 0-100, auto-declines >90 |
| Bank auth (simulated) | 95% approval rate (replaced in Phase 7 with real ISO 8583) |

---

## Interview Notes

**Q: "Walk me through what happens when a customer pays"**
> "The payment service receives the request, first checks Redis for idempotency (prevent duplicates). Then it runs fraud scoring — if score >90, auto-declines without calling the bank. For approved transactions, it calls the routing service which selects the best bank and sends an ISO 8583 authorization message via TCP. Bank responds with approve/decline code. We save the result, mark the order as paid, publish an SQS event for webhooks, and return the response — all in under 500ms."

**Q: "How do you prevent double charging?"**
> "Every payment request requires an Idempotency-Key header. We store key→response in Redis with 24-hour TTL. If the same key comes in again (network retry, user double-click), we return the cached response without reprocessing. The customer is never charged twice."

---

## Next Step

→ Continue to **Phase 6 Part 5: Capture & Void**
