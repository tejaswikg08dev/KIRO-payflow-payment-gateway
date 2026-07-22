# Hands-On Guide — Phase 10 Part 2: AWS SNS Email/SMS Integration

## Goal
- How AWS SNS sends emails and SMS
- Email templates (payment confirmation, refund, settlement)
- LocalStack testing (simulates SNS locally)

---

## How AWS SNS Works

```
SNS (Simple Notification Service):
├── You PUBLISH a message to a TOPIC
├── All SUBSCRIBERS to that topic receive the message
├── Subscribers can be: email, SMS, HTTP endpoint, SQS queue, Lambda

OUR SETUP:
├── Topic: payflow-email-notifications
│   └── Subscriber: buyer@gmail.com (when they make a payment)
├── Topic: payflow-sms-notifications  
│   └── Subscriber: +919876543210

FLOW:
1. Payment captured → payment-service publishes to SQS → notification-service reads
2. Notification-service: "Send payment confirmation email to buyer@gmail.com"
3. Calls AWS SNS: sns.publish(topicArn, message, subject)
4. SNS delivers email to buyer@gmail.com
5. Customer sees: "Your payment of ₹5,000 to TechShop was successful"
```

---

## Email Templates

```
PAYMENT_CONFIRMATION:
Subject: "Payment of ₹{amount} to {merchant_name} successful"
Body: "Hi {customer_name}, your payment of ₹{amount} to {merchant_name} 
       was successful on {date}. Transaction ID: {payment_id}."

REFUND_CONFIRMATION:
Subject: "Refund of ₹{amount} from {merchant_name} initiated"
Body: "Hi {customer_name}, a refund of ₹{amount} from {merchant_name}
       has been initiated. It will reflect in 5-7 business days."

SETTLEMENT_PROCESSED:
Subject: "Settlement of ₹{net_amount} processed"
Body: "Hi {merchant_name}, your settlement for {date} has been processed.
       Net amount: ₹{net_amount}. UTR: {payout_utr}."
```

---

## LocalStack Testing

```cmd
# Verify topics exist:
aws --endpoint-url=http://localhost:4566 sns list-topics --region ap-south-1

# Publish test message:
aws --endpoint-url=http://localhost:4566 sns publish \
  --topic-arn arn:aws:sns:ap-south-1:000000000000:payflow-email-notifications \
  --message "Test notification" \
  --region ap-south-1
```

---

## Next Step → Phase 10 Part 3
