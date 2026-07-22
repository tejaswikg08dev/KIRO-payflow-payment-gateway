# Hands-On Guide — Phase 6 Part 9: Controllers, Swagger & Full Testing

## Goal

By the end of Part 9, you will have:
- PaymentController with all endpoints annotated for Swagger
- OrderController with full documentation
- Complete end-to-end test: create order → authorize → capture → refund
- Swagger UI showing all payment endpoints
- Phase 6 COMPLETE
- Git commit

## Prerequisites

- Parts 1-8 completed (all payment logic implemented)
- Docker running (PostgreSQL + Redis)

---

## Step 9.1: PaymentController (Complete)

**File exists:** `payment-service/src/main/java/com/payflow/payment/controller/PaymentController.java`

All endpoints are already defined. Here's the complete controller with Swagger annotations:

```java
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment lifecycle: authorize, capture, void, refund")
public class PaymentController {

    private final PaymentProcessorService paymentProcessor;

    @PostMapping
    @Operation(summary = "Authorize a payment",
        description = "Customer submits card/UPI details. System checks fraud, "
            + "routes to bank, returns authorized/declined. "
            + "Requires Idempotency-Key header to prevent duplicates.")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(...)

    @PostMapping("/{paymentId}/capture")
    @Operation(summary = "Capture an authorized payment",
        description = "Confirms the charge. Money moves from customer to gateway. "
            + "Can capture full amount or partial (e.g., Uber: auth ₹500, capture ₹320)")
    public ResponseEntity<ApiResponse<PaymentResponse>> capturePayment(...)

    @PostMapping("/{paymentId}/void")
    @Operation(summary = "Void an authorized payment",
        description = "Cancels the authorization. Hold released on customer's card. "
            + "Only works on AUTHORIZED payments (not captured).")
    public ResponseEntity<ApiResponse<PaymentResponse>> voidPayment(...)

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a captured payment",
        description = "Returns money to customer. Can be full or partial. "
            + "Only works on CAPTURED/SETTLED payments.")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(...)

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(...)
}
```

---

## Step 9.2: Full End-to-End Test (Complete Flow)

Run these commands in order to simulate a real payment lifecycle:

### 1. Create Order (merchant initiates):
```cmd
curl -X POST http://localhost:8083/v1/orders ^
  -H "Content-Type: application/json" ^
  -H "X-Merchant-Id: merch_test" ^
  -d "{\"amount\":5000.00,\"currency\":\"INR\",\"receipt\":\"order_E2E_001\"}"
```
**Save:** `orderId` from response (e.g., `ord_abc123`)

### 2. Authorize Payment (customer pays):
```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: e2e_test_001" ^
  -H "X-Merchant-Id: merch_test" ^
  -d "{\"orderId\":\"ord_abc123\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\",\"holderName\":\"TEST USER\"}}"
```
**Save:** `paymentId` from response (e.g., `pay_xyz789`)
**Check:** status = "authorized", authCode present, riskScore shown

### 3. Get Payment Status:
```cmd
curl http://localhost:8083/v1/payments/pay_xyz789
```
**Check:** All fields populated correctly

### 4. Capture Payment (merchant confirms):
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_xyz789/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```
**Check:** status = "captured", capturedAmount = 5000.00

### 5. Refund Payment (customer returns product):
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_xyz789/refund ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":2000.00,\"reason\":\"Partial return - headphones\"}"
```
**Check:** refundId returned, refundedAmount = 2000.00

### 6. Get Final Payment State:
```cmd
curl http://localhost:8083/v1/payments/pay_xyz789
```
**Expected:** status = "captured", capturedAmount = 5000, refundedAmount = 2000

### 7. Refund Remaining:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_xyz789/refund ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":3000.00,\"reason\":\"Full return - remaining items\"}"
```
**Expected:** status = "refunded" (fully refunded: 2000 + 3000 = 5000)

---

## Step 9.3: Test Error Cases

### Try to capture a voided payment:
```cmd
# First authorize a new payment, then void it, then try capture
curl -X POST http://localhost:8083/v1/payments/pay_VOIDED/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```
**Expected 400:** `"Cannot capture. Current status: 'VOIDED'"`

### Try to refund more than captured:
```cmd
curl -X POST http://localhost:8083/v1/payments/pay_xyz789/refund ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":1000.00,\"reason\":\"Extra\"}"
```
**Expected 400:** `"Refund amount exceeds available"`

---

## Step 9.4: Open Swagger UI

**http://localhost:8083/swagger-ui.html**

You should see:
```
Orders:
  POST /v1/orders              Create a payment order
  GET  /v1/orders/{orderId}    Get order by ID

Payments:
  POST /v1/payments                      Authorize a payment
  POST /v1/payments/{paymentId}/capture  Capture
  POST /v1/payments/{paymentId}/void     Void
  POST /v1/payments/{paymentId}/refund   Refund
  GET  /v1/payments/{paymentId}          Get payment details
```

---

## Step 9.5: Git Commit

```cmd
git add .
git commit -m "Phase 6 Complete: Payment service - full lifecycle (order, auth, capture, void, refund, idempotency)"
```

---

## Phase 6 Complete! 🎉

| Part | What Was Built |
|------|---------------|
| Part 1 | Project setup, Flyway migrations (orders, payments, refunds tables) |
| Part 2 | Payment state machine (valid transition enforcement) |
| Part 3 | Order creation (POST /orders, 30-min expiry) |
| Part 4 | Payment authorization (fraud check, bank simulation, response) |
| Part 5 | Capture (full + partial) and Void |
| Part 6 | Refund (full + partial, amount validation) |
| Part 7 | Idempotency (Redis-based duplicate prevention) |
| Part 8 | Feign clients (routing-service integration + fallback) |
| Part 9 | Swagger, end-to-end testing, error cases |

**The Payment Service is the COMPLETE core of PayFlow.** It handles the entire payment lifecycle from order creation through settlement. The remaining phases (7-16) add routing/ISO 8583, settlement batch, webhooks, frontend, and deployment around this core.

---

## Next Step

→ Move to **Phase 7: Routing Service + ISO 8583 + Bank Simulator**

Phase 7 replaces the `simulateBankAuthorization()` with real ISO 8583 protocol communication to the bank simulator via TCP.
