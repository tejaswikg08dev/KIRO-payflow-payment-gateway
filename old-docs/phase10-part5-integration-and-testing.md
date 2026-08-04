# Hands-On Guide — Phase 10 Part 5: Integration & Testing

## Goal
- Fraud engine integrated into payment-service
- Notification service consuming SQS events
- Phase 10 COMPLETE
- Git commit

---

## How Fraud Integrates with Payment Flow

```
PaymentProcessorService.processPayment():
  ...
  // STEP 4: Fraud Check
  FraudResult fraud = fraudEngine.evaluate(transactionContext);
  payment.setRiskScore(fraud.score());

  if (fraud.decision().equals("DECLINE")) {
      payment.setStatus(FAILED);
      payment.setFailureCode("FRAUD_DETECTED");
      // Do NOT call bank — save time and money
      return response;
  }
  if (fraud.decision().equals("CHALLENGE")) {
      // Trigger 3D Secure (OTP verification)
      // Additional verification before proceeding to bank
  }
  // APPROVE or CHALLENGE (after OTP) → proceed to bank call
  ...
```

---

## How Notifications Integrate

```
FLOW:
Payment Service (after capture):
  → Publishes to SQS: payflow-notification queue
  → Message: { type: "EMAIL", template: "PAYMENT_CONFIRMATION", ... }

Notification Service:
  → Polls SQS continuously
  → Reads message
  → Fills template with data
  → Calls AWS SNS to send email
  → Deletes message from queue

RESULT: Customer gets email within seconds of payment!
```

---

## Testing All Together

```cmd
# 1. Start all infrastructure + all services
docker compose -f docker-compose-infra.yml up -d
# Start all 11 services...

# 2. Create order + payment (will trigger fraud check + notification)
curl -X POST http://localhost:8083/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: fraud_test_001" \
  -d '{"orderId":"ord_test","amount":75000.00,"method":"card","card":{"number":"4111111111111111","expiryMonth":12,"expiryYear":2028,"cvv":"123"}}'

# 3. Check risk score in response (should be elevated due to ₹75K amount)
# 4. Check notification-service logs for email attempt
# 5. Check webhook-service logs for delivery attempt
```

---

## Phase 10 Complete! 🎉

| Part | What Was Built |
|------|---------------|
| Part 1 | Notification service setup, SQS consumer concept |
| Part 2 | AWS SNS integration, email/SMS templates |
| Part 3 | Fraud rule engine (velocity, amount, geo, device) |
| Part 4 | AI/ML scoring with Decision Tree model |
| Part 5 | Integration into payment flow, testing |

---

## Next Step → Phase 11: React Frontend
