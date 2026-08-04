# Hands-On Guide — Phase 9 Part 6: Controllers, Swagger & Testing

## Goal
- Webhook debugging endpoints (list events, get event detail, manual retry)
- Swagger UI with all webhook management APIs
- Phase 9 COMPLETE

---

## Endpoints (http://localhost:8086/swagger-ui.html)

```
Webhook Events:
  GET  /v1/webhooks/events                    List events (filter by status)
  GET  /v1/webhooks/events/{eventId}          Get event detail (delivery attempts)
  POST /v1/webhooks/events/{eventId}/retry    Manually retry a failed delivery
```

These are for MERCHANTS to debug webhook issues:
- "Why didn't I receive the payment.captured event?"
- "What was the error when delivery failed?"
- "Retry this event now that I've fixed my server"

---

## Test Flow

```cmd
# 1. Create and capture a payment (triggers payment.captured event)
# 2. Webhook service picks up from SQS, tries to deliver
# 3. If merchant URL is unreachable, mark as FAILED
# 4. Check failed events:
curl http://localhost:8086/v1/webhooks/events?status=failed
# 5. Manually retry:
curl -X POST http://localhost:8086/v1/webhooks/events/evt_xxx/retry
```

---

## Interview Notes

**Q: "How can merchants debug webhook issues?"**
> "We provide a delivery log API where merchants can see all webhook events, their delivery status, attempt count, response codes from their server, and error messages. They can manually retry any failed event from their dashboard. We also track X-PayFlow-Delivery-Id so they can correlate logs."

---

## Next Step → Phase 10: Notification + Fraud Detection
