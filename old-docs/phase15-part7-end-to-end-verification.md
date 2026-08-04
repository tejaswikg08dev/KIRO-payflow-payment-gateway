# Hands-On Guide — Phase 15 Part 7: End-to-End Verification on AWS

## Goal

By the end of Part 7 you will have:
- Full payment flow tested on AWS (register → pay → capture → settle)
- All services verified healthy
- Frontend accessing backend via ALB
- DynamoDB, SQS, SNS all working
- Confidence that the deployment is production-ready

## Prerequisites

- Parts 1-6 completed (all AWS resources running)
- ALB DNS name available
- Frontend deployed to CloudFront

---

## Step 7.1: Verify All Services Healthy

```cmd
:: Check via ALB
curl http://payflow-alb-XXX.ap-south-1.elb.amazonaws.com/actuator/health

:: SSH into EC2 and check all containers
ssh -i payflow-key.pem ec2-user@EC2_IP
docker ps
:: Should show 11+ containers running

:: Check each service health
curl http://localhost:8081/actuator/health  # identity
curl http://localhost:8082/actuator/health  # merchant
curl http://localhost:8083/actuator/health  # payment
curl http://localhost:8084/actuator/health  # routing
curl http://localhost:8085/actuator/health  # settlement
curl http://localhost:8086/actuator/health  # webhook
curl http://localhost:8087/actuator/health  # notification
```

All should return: `{"status":"UP"}`

---

## Step 7.2: Test Full Payment Flow on AWS

### 1. Register User:
```cmd
curl -X POST http://payflow-alb-XXX.elb.amazonaws.com/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"aws-test@payflow.com\",\"password\":\"AwsTest2026!\",\"fullName\":\"AWS Test User\",\"role\":\"MERCHANT\"}"
```
**Expected:** 201 with JWT tokens ✅

### 2. Create Merchant:
```cmd
curl -X POST http://payflow-alb-XXX.elb.amazonaws.com/v1/merchants ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\":\"USR_ID_FROM_STEP1\",\"businessName\":\"AWS Test Shop\",\"businessType\":\"COMPANY\"}"
```
**Expected:** 201 with merchant_id ✅

### 3. Generate API Key:
```cmd
curl -X POST "http://payflow-alb-XXX.elb.amazonaws.com/v1/merchants/MERCHANT_ID/api-keys?keyType=LIVE"
```
**Expected:** 201 with public_key + secret_key ✅

### 4. Create Order:
```cmd
curl -X POST http://payflow-alb-XXX.elb.amazonaws.com/v1/orders ^
  -H "X-Api-Key: sk_pay_XXXXX" ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00,\"currency\":\"INR\",\"receipt\":\"aws_test_001\"}"
```
**Expected:** 201 with order_id ✅

### 5. Authorize Payment:
```cmd
curl -X POST http://payflow-alb-XXX.elb.amazonaws.com/v1/payments ^
  -H "X-Api-Key: sk_pay_XXXXX" ^
  -H "Idempotency-Key: aws_test_pay_001" ^
  -H "Content-Type: application/json" ^
  -d "{\"orderId\":\"ORDER_ID\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\"}}"
```
**Expected:** 201 with status "authorized", authCode, rrn ✅

### 6. Capture Payment:
```cmd
curl -X POST http://payflow-alb-XXX.elb.amazonaws.com/v1/payments/PAYMENT_ID/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":5000.00}"
```
**Expected:** 200 with status "captured" ✅

### 7. Check DynamoDB (Webhook Event Stored):
```cmd
aws dynamodb scan --table-name payflow-webhook-events --region ap-south-1
```
**Expected:** Event records for payment.authorized and payment.captured ✅

### 8. Check SQS (Messages Processed):
```cmd
aws sqs get-queue-attributes ^
  --queue-url https://sqs.ap-south-1.amazonaws.com/ACCOUNT_ID/payflow-payment-events ^
  --attribute-names ApproximateNumberOfMessages ^
  --region ap-south-1
```
**Expected:** 0 messages (all consumed by webhook-service) ✅

---

## Step 7.3: Test Frontend

1. Open: `https://dXXXXXXXXXX.cloudfront.net`
2. Register / Login
3. View dashboard (should show the payment we just made)
4. Check transactions page

---

## Step 7.4: Final Architecture Running

```
PRODUCTION ARCHITECTURE (what you just verified):

Internet Users
     │
     ▼
CloudFront (frontend) ──── S3 (static files)     [$0/month]
     │
     ▼
ALB (load balancer) ──── HTTPS termination        [~$16/month]
     │
     ▼
EC2 (Docker containers)                           [~$8.50/month]
├── api-gateway (8080)
├── identity-service (8081)
├── merchant-service (8082)
├── payment-service (8083)
├── routing-service (8084)
├── settlement-service (8085)
├── webhook-service (8086)
├── notification-service (8087)
└── bank-simulator (9000)
     │
     ├──► RDS PostgreSQL (private subnet)          [~$15/month]
     ├──► ElastiCache Redis (private subnet)       [~$12/month]
     ├──► DynamoDB (managed)                       [$0/month]
     ├──► SQS queues (managed)                     [$0/month]
     └──► SNS topics (managed)                     [$0/month]

TOTAL: ~$51.50/month (covered by $200 credits for ~4 months)
```

---

## 🎉 DEPLOYMENT COMPLETE!

Your payment gateway is live on AWS. You can:
- Access the API via ALB URL
- Use the merchant dashboard via CloudFront URL
- Process test payments end-to-end
- Show this in interviews as a working demo

---

## Git Commit

```cmd
git commit -m "Phase 15 Part 7: End-to-end verification on AWS - full payment flow working"
```

---

## Next Step

→ Continue to **Phase 15 Part 8: Teardown Guide (Save Credits)**
