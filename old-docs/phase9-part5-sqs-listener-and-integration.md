# Hands-On Guide — Phase 9 Part 5: SQS Listener & Integration

## Goal
- How webhook-service listens to SQS queue
- Integration with payment-service (payment publishes → webhook consumes)
- Message flow: payment.captured event → SQS → webhook delivery

---

## How SQS Integration Works

```
PUBLISHER (payment-service):
  After capturing a payment:
    sqsClient.sendMessage("payflow-payment-events", {
      event_type: "payment.captured",
      merchant_id: "merch_xyz",
      data: { payment_id: "pay_abc", amount: 5000, ... }
    });
  → Message goes to SQS queue (stored until consumed)

CONSUMER (webhook-service):
  Polls SQS every few seconds:
    List<Message> messages = sqsClient.receiveMessages("payflow-payment-events");
    for (Message msg : messages) {
      1. Parse event JSON
      2. Look up merchant's webhook_url and webhook_secret
      3. Sign and deliver webhook (WebhookDispatcher)
      4. If success → delete message from queue
      5. If fail → don't delete (SQS will redeliver after visibility timeout)
    }

WHY SQS (not direct HTTP)?
├── DECOUPLING: Payment-service doesn't wait for webhook delivery
├── RETRY: Failed messages stay in queue automatically
├── BUFFERING: If webhook-service is down, messages wait safely
├── ORDERING: FIFO queue ensures events arrive in order
└── DLQ: Built-in dead letter queue after N failures
```

---

## SQS Configuration

```yaml
# Queues (created by docker/init-localstack.sh):
payflow-payment-events          → main event queue
payflow-payment-events-dlq      → dead letter (after 3 SQS failures)

# Settings:
visibility-timeout: 60 seconds   → message invisible to others while processing
max-receive-count: 3             → after 3 failed receives → moves to DLQ
message-retention: 4 days        → unprocessed messages kept 4 days then deleted
```

---

## Phase 9 COMPLETE! 🎉

| Part | What Was Built |
|------|---------------|
| Part 1 | Webhook service setup, DynamoDB table concept |
| Part 2 | Event types and JSON payload schemas |
| Part 3 | HMAC-SHA256 signature generation + verification |
| Part 4 | Exponential backoff retry + Dead Letter Queue |
| Part 5 | SQS listener integration with payment-service |

---

## Next Step → Phase 10: Notification + Fraud Detection
