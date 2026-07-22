# Hands-On Guide — Phase 15 Part 8: Teardown Guide (Save Credits)

## Goal

By the end of Part 8 you will have:
- All paid AWS resources deleted (EC2, RDS, ElastiCache, ALB)
- Free resources kept running (DynamoDB, SQS, SNS, S3, CloudFront)
- Credits preserved for future use
- Understanding of what to delete vs what to keep

## Prerequisites

- E2E verification complete (Part 7 — you've confirmed everything works)
- Ready to stop paying (demo/interview done)

---

## Why Teardown?

```
WHILE RUNNING:
├── EC2 × 1: ~$8.50/month
├── RDS: ~$15/month
├── ElastiCache: ~$12/month
├── ALB: ~$16/month
└── TOTAL: ~$51.50/month eating credits!

AFTER TEARDOWN:
├── DynamoDB: $0 (always free)
├── SQS: $0 (always free)
├── SNS: $0 (always free)
├── S3: $0 (always free under 5GB)
├── CloudFront: $0 (always free under 1TB)
└── TOTAL: $0/month!

Your frontend stays live on CloudFront (free!).
Backend APIs go offline until you redeploy.
Code is on GitHub — you can redeploy anytime.
```

---

## Step 8.1: Delete ALB (Saves ~$16/month)

```cmd
:: Delete listener first
aws elbv2 describe-listeners --load-balancer-arn %ALB_ARN% --query "Listeners[*].ListenerArn" --output text --region ap-south-1
aws elbv2 delete-listener --listener-arn LISTENER_ARN --region ap-south-1

:: Delete target group
aws elbv2 delete-target-group --target-group-arn %TARGET_GROUP_ARN% --region ap-south-1

:: Delete ALB
aws elbv2 delete-load-balancer --load-balancer-arn %ALB_ARN% --region ap-south-1
```

---

## Step 8.2: Terminate EC2 (Saves ~$8.50/month)

```cmd
:: Terminate (permanently delete) EC2 instance
aws ec2 terminate-instances --instance-ids i-0xxxxx --region ap-south-1

:: Verify it's terminating
aws ec2 describe-instances --instance-ids i-0xxxxx --query "Reservations[0].Instances[0].State.Name" --output text --region ap-south-1
:: Should show: "shutting-down" then "terminated"
```

**⚠️ After termination: instance and its data are GONE forever.**
(Our code is on GitHub, so nothing is lost.)

---

## Step 8.3: Delete RDS (Saves ~$15/month)

```cmd
:: Delete WITHOUT final snapshot (saves storage cost)
aws rds delete-db-instance ^
  --db-instance-identifier payflow-postgres ^
  --skip-final-snapshot ^
  --delete-automated-backups ^
  --region ap-south-1
```

**⏳ Takes 5-10 minutes.**

**⚠️ All data in RDS is DELETED.** But we don't need it — our schema is in
Flyway migrations (code), and test data can be recreated.

---

## Step 8.4: Delete ElastiCache (Saves ~$12/month)

```cmd
aws elasticache delete-cache-cluster ^
  --cache-cluster-id payflow-redis ^
  --region ap-south-1
```

---

## Step 8.5: Release Elastic IPs (If Any)

Unattached Elastic IPs cost $3.60/month! Check and release:

```cmd
:: List all EIPs
aws ec2 describe-addresses --query "Addresses[*].{AllocationId:AllocationId,InstanceId:InstanceId}" --output table --region ap-south-1

:: Release any that show "InstanceId: None" (unattached)
aws ec2 release-address --allocation-id eipalloc-xxxxx --region ap-south-1
```

---

## Step 8.6: Delete VPC Resources (Optional — $0 Cost)

VPC itself is free, but if you want a clean slate:

```cmd
:: Delete security groups (can't delete default)
aws ec2 delete-security-group --group-id %ALB_SG% --region ap-south-1
aws ec2 delete-security-group --group-id %APP_SG% --region ap-south-1
aws ec2 delete-security-group --group-id %DB_SG% --region ap-south-1

:: Detach and delete internet gateway
aws ec2 detach-internet-gateway --internet-gateway-id %IGW_ID% --vpc-id %VPC_ID% --region ap-south-1
aws ec2 delete-internet-gateway --internet-gateway-id %IGW_ID% --region ap-south-1

:: Delete subnets
aws ec2 delete-subnet --subnet-id %PUBLIC_SUBNET_1A% --region ap-south-1
aws ec2 delete-subnet --subnet-id %PUBLIC_SUBNET_1B% --region ap-south-1
aws ec2 delete-subnet --subnet-id %PRIVATE_SUBNET_1A% --region ap-south-1
aws ec2 delete-subnet --subnet-id %PRIVATE_SUBNET_1B% --region ap-south-1

:: Delete VPC
aws ec2 delete-vpc --vpc-id %VPC_ID% --region ap-south-1
```

---

## Step 8.7: Keep These (Free Forever)

**DO NOT DELETE these — they cost $0:**

| Resource | Why Keep |
|----------|---------|
| DynamoDB tables | Always free, data preserved |
| SQS queues | Always free, empty queues cost nothing |
| SNS topics | Always free |
| S3 bucket | Always free (under 5 GB), frontend stays live |
| CloudFront | Always free (under 1 TB), dashboard keeps working |
| CloudWatch logs | Free tier (5 GB), useful for debugging |

---

## Step 8.8: Verify Teardown Complete

```cmd
:: Should show NO running instances
aws ec2 describe-instances --filters "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output text --region ap-south-1

:: Should show NO RDS instances
aws rds describe-db-instances --query "DBInstances[*].DBInstanceIdentifier" --output text --region ap-south-1

:: Should show NO ElastiCache clusters
aws elasticache describe-cache-clusters --query "CacheClusters[*].CacheClusterId" --output text --region ap-south-1

:: Should show NO ALBs
aws elbv2 describe-load-balancers --query "LoadBalancers[*].LoadBalancerName" --output text --region ap-south-1
```

**All should return empty (no results).**

---

## After Teardown: Monthly Cost = $0

```
RESOURCES REMAINING (all free):
├── DynamoDB: 3 tables (within 25 GB free) → $0
├── SQS: 4 queues (within 1M messages free) → $0
├── SNS: 2 topics (within 1M publishes free) → $0
├── S3: 1 bucket with frontend files → $0
├── CloudFront: 1 distribution → $0
└── TOTAL: $0/month ✅

CREDITS PRESERVED:
├── Used: ~$51.50 × (weeks running / 4)
├── If ran for 2 weeks: ~$26 used, ~$174 remaining
└── Can redeploy anytime with remaining credits!
```

---

## How to Redeploy Later (When Needed for Interview Demo)

```cmd
1. Recreate VPC + subnets (Phase 15 Part 2 commands)
2. Recreate RDS + Redis (Phase 15 Part 3 commands)
3. Launch EC2 + deploy Docker (Phase 15 Part 4 commands)
4. Create ALB (Phase 15 Part 5 commands)
5. Update frontend API URL → redeploy to S3
6. Total time: ~30 minutes
```

---

## Git Commit

```cmd
git commit -m "Phase 15 Part 8: AWS teardown guide - delete paid resources, keep free ones"
```

---

## Phase 15 COMPLETE! 🎉

| Part | What Was Done |
|------|--------------|
| Part 1 | AWS account, IAM, MFA, CLI, budget alerts |
| Part 2 | VPC, subnets (public + private), security groups |
| Part 3 | RDS PostgreSQL, ElastiCache Redis, DynamoDB, SQS, SNS |
| Part 4 | EC2 launch, Docker install, service deployment |
| Part 5 | ALB with target groups, health checks, routing |
| Part 6 | S3 + CloudFront for React frontend |
| Part 7 | End-to-end verification (full payment flow on AWS) |
| Part 8 | Teardown (delete paid, keep free, preserve credits) |

---

## Next Step

→ Move to **Phase 16: Monitoring & Observability**
