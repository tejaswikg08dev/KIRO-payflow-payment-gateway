# Hands-On Guide — Phase 10 Part 1: Notification Service Setup

## Goal
- notification-service running on port 8087
- Understanding of AWS SNS for email/SMS
- SQS listener consuming notification events
- Git commit

---

## What Notification Service Does

```
Listens to SQS queue: payflow-notification

Messages arrive like:
{
  "type": "EMAIL",
  "template": "PAYMENT_CONFIRMATION",
  "recipient": { "email": "buyer@gmail.com", "name": "Rajesh" },
  "data": { "amount": 5000, "merchant_name": "TechShop", ... }
}

Service processes:
1. Read message from SQS
2. Determine channel (EMAIL or SMS)
3. Fill template with data
4. Send via AWS SNS:
   - Email: SNS → SES (Simple Email Service) → buyer's inbox
   - SMS: SNS → SMS gateway → buyer's phone
5. Delete message from queue (processed successfully)
```

---

## Source Code

```
notification-service/
├── pom.xml
├── src/main/java/com/payflow/notification/
│   └── NotificationServiceApplication.java
└── src/main/resources/application.yml (port 8087, SNS config)
```

---

## AWS SNS Configuration (LocalStack for local dev)

```yaml
aws:
  sns:
    endpoint: http://localhost:4566      # LocalStack simulates SNS
    region: ap-south-1
    email-topic-arn: arn:aws:sns:ap-south-1:000000000000:payflow-email-notifications
```

In production: replace endpoint with real AWS SNS (no endpoint override needed).

---

## Next Step → Phase 10 Parts 2-5
