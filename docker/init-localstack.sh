#!/bin/bash
# This script runs when LocalStack starts.
# It creates the SQS queues and SNS topics we need.

echo "Creating SQS queues..."

# Payment events queue (consumed by webhook-service)
awslocal sqs create-queue --queue-name payflow-payment-events

# Webhook delivery queue (retry queue for webhook-service)
awslocal sqs create-queue --queue-name payflow-webhook-delivery

# Notification queue (consumed by notification-service)
awslocal sqs create-queue --queue-name payflow-notification

# Dead letter queues
awslocal sqs create-queue --queue-name payflow-payment-events-dlq
awslocal sqs create-queue --queue-name payflow-webhook-delivery-dlq

echo "Creating SNS topics..."

# Email notifications
awslocal sns create-topic --name payflow-email-notifications

# SMS notifications
awslocal sns create-topic --name payflow-sms-notifications

echo "LocalStack initialization complete!"
echo "SQS endpoint: http://localhost:4566"
echo "SNS endpoint: http://localhost:4566"
