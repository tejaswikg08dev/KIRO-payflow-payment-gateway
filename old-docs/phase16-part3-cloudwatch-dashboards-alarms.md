# Phase 16 Part 3 — CloudWatch Dashboards & Alarms

## Goal
- Create CloudWatch dashboards for service health visibility
- Configure alarms for error rates, latency, and resource usage
- Set up SNS notifications for critical alerts

## Key Concept

```
┌────────────────────────────────────────────────────────────┐
│  Monitoring Stack                                          │
│                                                            │
│  Services → CloudWatch Logs → Metric Filters → Alarms     │
│                                                    │       │
│                                                    ▼       │
│  CloudWatch Dashboard          SNS Topic → Email/Slack     │
│  ┌──────────────────────────┐                              │
│  │ ┌──────┐ ┌──────┐       │                              │
│  │ │ RPS  │ │ P99  │       │                              │
│  │ │Chart │ │Latency│       │                              │
│  │ └──────┘ └──────┘       │                              │
│  │ ┌──────┐ ┌──────┐       │                              │
│  │ │Error │ │ CPU  │       │                              │
│  │ │ Rate │ │ Usage│       │                              │
│  │ └──────┘ └──────┘       │                              │
│  └──────────────────────────┘                              │
└────────────────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 16 Part 2 completed (structured logging in place)
- AWS CloudWatch agent installed on EC2 (or logs sent via Docker log driver)
- Services writing JSON logs to stdout (collected by CloudWatch)

## Step-by-Step

### 1. Configure CloudWatch Log Driver (docker-compose.prod.yml)

```yaml
services:
  payment-service:
    logging:
      driver: awslogs
      options:
        awslogs-group: /payflow/payment-service
        awslogs-region: us-east-1
        awslogs-stream-prefix: ecs
        awslogs-create-group: "true"
```

### 2. Create Metric Filters (extract metrics from logs)

```bash
# Error count metric
aws logs put-metric-filter \
  --log-group-name /payflow/payment-service \
  --filter-name PaymentErrors \
  --filter-pattern '{ $.level = "ERROR" }' \
  --metric-transformations \
    metricName=ErrorCount,metricNamespace=PayFlow,metricValue=1,defaultValue=0

# Payment success metric
aws logs put-metric-filter \
  --log-group-name /payflow/payment-service \
  --filter-name PaymentSuccess \
  --filter-pattern '{ $.message = "Payment completed successfully" }' \
  --metric-transformations \
    metricName=PaymentSuccessCount,metricNamespace=PayFlow,metricValue=1,defaultValue=0

# Payment failure metric
aws logs put-metric-filter \
  --log-group-name /payflow/payment-service \
  --filter-name PaymentFailures \
  --filter-pattern '{ $.message = "Payment failed*" }' \
  --metric-transformations \
    metricName=PaymentFailureCount,metricNamespace=PayFlow,metricValue=1,defaultValue=0
```

### 3. Create SNS Topic for Alerts

```bash
aws sns create-topic --name payflow-alerts
# Note TopicArn

aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:ACCOUNT:payflow-alerts \
  --protocol email \
  --notification-endpoint alerts@payflow.example.com
# Confirm subscription via email
```

### 4. Create Alarms

```bash
# High error rate alarm (>10 errors in 5 minutes)
aws cloudwatch put-metric-alarm \
  --alarm-name payflow-high-error-rate \
  --alarm-description "Payment service error rate too high" \
  --namespace PayFlow \
  --metric-name ErrorCount \
  --statistic Sum \
  --period 300 \
  --threshold 10 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT:payflow-alerts

# API Gateway 5xx alarm
aws cloudwatch put-metric-alarm \
  --alarm-name payflow-alb-5xx \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPCode_Target_5XX_Count \
  --dimensions Name=LoadBalancer,Value=app/payflow-alb/xxx \
  --statistic Sum \
  --period 60 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT:payflow-alerts

# High latency alarm (p99 > 3 seconds)
aws cloudwatch put-metric-alarm \
  --alarm-name payflow-high-latency \
  --namespace AWS/ApplicationELB \
  --metric-name TargetResponseTime \
  --dimensions Name=LoadBalancer,Value=app/payflow-alb/xxx \
  --extended-statistic p99 \
  --period 300 \
  --threshold 3.0 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT:payflow-alerts

# RDS CPU alarm
aws cloudwatch put-metric-alarm \
  --alarm-name payflow-rds-cpu-high \
  --namespace AWS/RDS \
  --metric-name CPUUtilization \
  --dimensions Name=DBInstanceIdentifier,Value=payflow-db \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 3 \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT:payflow-alerts
```

### 5. Create Dashboard

```bash
aws cloudwatch put-dashboard --dashboard-name PayFlow-Production \
  --dashboard-body '{
  "widgets": [
    {
      "type": "metric",
      "x": 0, "y": 0, "width": 12, "height": 6,
      "properties": {
        "title": "Request Rate",
        "metrics": [
          ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", "app/payflow-alb/xxx", {"stat":"Sum","period":60}]
        ],
        "view": "timeSeries"
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 0, "width": 12, "height": 6,
      "properties": {
        "title": "Response Time (p50, p99)",
        "metrics": [
          ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", "app/payflow-alb/xxx", {"stat":"p50"}],
          ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", "app/payflow-alb/xxx", {"stat":"p99"}]
        ],
        "view": "timeSeries"
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 6, "width": 12, "height": 6,
      "properties": {
        "title": "Error Rates (4xx vs 5xx)",
        "metrics": [
          ["AWS/ApplicationELB", "HTTPCode_Target_4XX_Count", "LoadBalancer", "app/payflow-alb/xxx", {"stat":"Sum","period":60}],
          ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", "app/payflow-alb/xxx", {"stat":"Sum","period":60,"color":"#d62728"}]
        ],
        "view": "timeSeries"
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 6, "width": 12, "height": 6,
      "properties": {
        "title": "Payment Outcomes",
        "metrics": [
          ["PayFlow", "PaymentSuccessCount", {"stat":"Sum","period":300,"color":"#2ca02c"}],
          ["PayFlow", "PaymentFailureCount", {"stat":"Sum","period":300,"color":"#d62728"}]
        ],
        "view": "timeSeries"
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 12, "width": 8, "height": 6,
      "properties": {
        "title": "RDS CPU",
        "metrics": [["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", "payflow-db"]],
        "view": "timeSeries",
        "annotations": {"horizontal":[{"value":80,"color":"#d62728","label":"Alarm threshold"}]}
      }
    },
    {
      "type": "metric",
      "x": 8, "y": 12, "width": 8, "height": 6,
      "properties": {
        "title": "Redis Memory",
        "metrics": [["AWS/ElastiCache", "DatabaseMemoryUsagePercentage", "ReplicationGroupId", "payflow-redis"]]
      }
    }
  ]
}'
```

## Verification

```bash
# Check dashboard exists
aws cloudwatch list-dashboards --query "DashboardEntries[?DashboardName=='PayFlow-Production']"

# Check alarms
aws cloudwatch describe-alarms --alarm-name-prefix payflow \
  --query "MetricAlarms[*].[AlarmName,StateValue]" --output table
# All should be "OK"

# Trigger a test alarm (temporarily lower threshold)
# Or generate errors and check SNS notification arrives

# View dashboard in console
echo "https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=PayFlow-Production"
```

## Git Commit

```bash
git add docs/phase16-part3-cloudwatch-dashboards-alarms.md
git commit -m "docs: add CloudWatch monitoring dashboards and alarms guide"
```

## Next Step
→ Congratulations! The PayFlow Payment Gateway is fully documented from frontend to production monitoring.
