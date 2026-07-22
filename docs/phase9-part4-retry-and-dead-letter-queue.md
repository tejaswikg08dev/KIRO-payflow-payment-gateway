# Hands-On Guide — Phase 9 Part 4: Retry & Dead Letter Queue

## Goal
- Exponential backoff retry strategy (5 attempts over 24 hours)
- Dead Letter Queue (DLQ) for permanently failed webhooks
- Understanding of at-least-once delivery guarantee

---

## Retry Strategy

```
ATTEMPT 1: Immediately (when event happens)
  └── Merchant server returns 500? → schedule retry

ATTEMPT 2: After 5 minutes
  └── Merchant server timeout? → schedule retry

ATTEMPT 3: After 30 minutes
  └── Connection refused? → schedule retry

ATTEMPT 4: After 2 hours
  └── Still failing? → one more try

ATTEMPT 5: After 24 hours
  └── STILL failing? → give up → move to Dead Letter Queue (DLQ)

EXPONENTIAL BACKOFF:
├── Delays increase: 0, 5m, 30m, 2h, 24h
├── Why? Gives merchant time to fix their server
├── After each failure: record attempt_count, last_error, next_retry_at
└── Merchant can see delivery status in dashboard

WHAT TRIGGERS RETRY:
├── HTTP 5xx (server error) → retry (server might recover)
├── Timeout (>10 seconds) → retry (temporary network issue)
├── Connection refused → retry (server might restart)

WHAT DOES NOT RETRY:
├── HTTP 4xx (client error) → permanent failure (bad endpoint URL)
├── Except 429 (rate limited) → retry after delay
└── HTTP 2xx → success! No retry needed.
```

---

## Dead Letter Queue (DLQ)

```
AFTER 5 FAILED ATTEMPTS:
├── Event moves to DLQ (separate SQS queue: payflow-webhook-delivery-dlq)
├── Operations team is alerted (SNS email)
├── Event stays in DLQ for manual investigation
├── Merchant can manually retry from dashboard:
│   POST /v1/webhooks/events/{eventId}/retry
└── Admin can investigate: why is merchant's server consistently failing?

COMMON CAUSES:
├── Merchant's server is permanently down
├── Merchant changed webhook URL but didn't update in PayFlow
├── Merchant's SSL certificate expired
├── Merchant's server has a bug processing our payload
└── Network issue between our VPC and merchant's server
```

---

## Interview Notes

**Q: "How do you ensure webhook delivery?"**
> "At-least-once delivery with exponential backoff. We attempt 5 times over 24 hours (immediate, 5min, 30min, 2hr, 24hr). If all fail, the event goes to a Dead Letter Queue where ops investigates. Merchants can also manually retry from their dashboard."

**Q: "Why at-least-once and not exactly-once?"**
> "Exactly-once delivery is impossible in distributed systems (network can always fail between 'delivered' and 'acknowledged'). We guarantee at-least-once. Merchants handle duplicates by checking the event_id — if they've already processed it, they ignore the duplicate and return 200."

---

## Next Step → Phase 9 Parts 5-6
