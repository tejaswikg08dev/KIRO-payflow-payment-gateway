# Hands-On Guide — Phase 9 Part 1: Webhook Service — Setup & DynamoDB

## Goal
- webhook-service running on port 8086
- Understanding of DynamoDB for event storage
- WebhookServiceApplication with scheduling enabled
- Git commit

## Prerequisites
- Phase 8 completed
- Docker running (LocalStack for DynamoDB)

---

## Why DynamoDB (Not PostgreSQL) for Webhooks?

```
Webhook events are HIGH-WRITE, EVENTUAL-READ:
├── Every payment generates 1-3 events
├── At 1000 TPS payments = 1000-3000 event writes/second
├── Events are written once, read occasionally (debugging)
├── Events auto-delete after 30 days (TTL)
├── No complex queries needed (just: find by merchant, find pending)

PostgreSQL: Great for ACID transactions (payments)
DynamoDB: Great for high-write event logs (webhooks)
├── Auto-scales writes without config
├── Always free (25 GB + 25 WCU)
├── Built-in TTL (auto-delete old records)
└── No connection pool limits
```

---

## Source Code

```
webhook-service/
├── pom.xml
├── src/main/java/com/payflow/webhook/
│   ├── WebhookServiceApplication.java       ← @EnableScheduling
│   ├── service/SignatureGenerator.java       ← HMAC-SHA256 signing
│   └── service/WebhookDispatcher.java       ← HTTP POST to merchant
└── src/main/resources/application.yml        ← Port 8086
```

---

## How to Run

```cmd
cd webhook-service
mvn spring-boot:run
# Swagger: http://localhost:8086/swagger-ui.html
```

---

## Next Step → Phase 9 Parts 2-6
