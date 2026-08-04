# Hands-On Guide — Phase 15 Part 3: RDS PostgreSQL, ElastiCache Redis & DynamoDB

## Goal

By the end of Part 3 you will have:
- RDS PostgreSQL instance (db.t3.micro) running in private subnet
- ElastiCache Redis node (cache.t3.micro) running in private subnet
- DynamoDB tables created (webhook_events, routing_metrics, audit_trail)
- DB subnet group configured (tells RDS which subnets to use)
- All credentials saved securely
- Services can connect from EC2 (same VPC, correct security groups)

## Prerequisites

- Phase 15 Part 2 completed (VPC, subnets, security groups exist)
- All resource IDs saved in aws-resources.txt
- AWS CLI configured and working

---

## What Each Database Does

```
OUR 3 DATABASES:

┌────────────────────────────────────────────────────────────────────────┐
│                                                                        │
│  PostgreSQL (RDS)         Redis (ElastiCache)       DynamoDB           │
│  ─────────────────        ──────────────────        ────────           │
│                                                                        │
│  WHAT: Relational DB      WHAT: In-memory cache     WHAT: NoSQL DB    │
│  WHY: ACID transactions   WHY: Microsecond reads    WHY: High-write   │
│  STORES:                  STORES:                   STORES:            │
│  ├── Users               ├── Idempotency keys      ├── Webhook events │
│  ├── Merchants           ├── Rate limit counters   ├── Routing metrics│
│  ├── Payments            ├── JWT blacklist         ├── Audit trail    │
│  ├── Orders              ├── Routing cache         │                  │
│  └── Settlements         └── Session data          │                  │
│                                                                        │
│  COST: ~$15/month        COST: ~$12/month          COST: $0 (free!)  │
│  (from $200 credits)     (from $200 credits)       (always free)      │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Step 3.1: Create DB Subnet Group

**What is this?** Tells RDS "use THESE subnets for the database."
RDS needs to know which subnets it can place instances in.

```cmd
aws rds create-db-subnet-group ^
  --db-subnet-group-name payflow-db-subnet-group ^
  --db-subnet-group-description "Subnets for PayFlow RDS" ^
  --subnet-ids %PRIVATE_SUBNET_1A% %PRIVATE_SUBNET_1B% ^
  --region ap-south-1
```

**Why both private subnets?** RDS requires at least 2 subnets in different AZs
(even for single-AZ deployment). It's an AWS requirement.

**Expected output:**
```json
{
    "DBSubnetGroup": {
        "DBSubnetGroupName": "payflow-db-subnet-group",
        "SubnetGroupStatus": "Complete"
    }
}
```

---

## Step 3.2: Create RDS PostgreSQL Instance

```cmd
aws rds create-db-instance ^
  --db-instance-identifier payflow-postgres ^
  --db-instance-class db.t3.micro ^
  --engine postgres ^
  --engine-version 15.4 ^
  --master-username payflow_admin ^
  --master-user-password PayFlow2026SecureDB! ^
  --allocated-storage 20 ^
  --db-name payflow ^
  --vpc-security-group-ids %DB_SG% ^
  --db-subnet-group-name payflow-db-subnet-group ^
  --no-multi-az ^
  --no-publicly-accessible ^
  --backup-retention-period 1 ^
  --storage-type gp2 ^
  --region ap-south-1
```

**Explanation of each flag:**

| Flag | Value | Why |
|------|-------|-----|
| `--db-instance-identifier` | payflow-postgres | Unique name for this DB |
| `--db-instance-class` | db.t3.micro | Smallest/cheapest (2 vCPU, 1 GB RAM) |
| `--engine` | postgres | PostgreSQL (not MySQL, not Aurora) |
| `--engine-version` | 15.4 | PostgreSQL 15 (same as our Docker version) |
| `--master-username` | payflow_admin | Login username |
| `--master-user-password` | PayFlow2026SecureDB! | Login password (**SAVE THIS!**) |
| `--allocated-storage` | 20 | 20 GB SSD (minimum, sufficient for demo) |
| `--db-name` | payflow | Create database named "payflow" |
| `--vpc-security-group-ids` | %DB_SG% | Only allow connections from APP security group |
| `--db-subnet-group-name` | payflow-db-subnet-group | Place in private subnets |
| `--no-multi-az` | — | Single AZ (saves money, fine for demo) |
| `--no-publicly-accessible` | — | **NOT reachable from internet** (security!) |
| `--backup-retention-period` | 1 | Keep backups for 1 day (minimum) |
| `--storage-type` | gp2 | General purpose SSD |

**⏳ This takes 5-10 minutes to create!** Wait for status to become "available".

### Check Status:
```cmd
aws rds describe-db-instances ^
  --db-instance-identifier payflow-postgres ^
  --query "DBInstances[0].{Status:DBInstanceStatus,Endpoint:Endpoint.Address,Port:Endpoint.Port}" ^
  --output table ^
  --region ap-south-1
```

**Wait until Status = "available"** (refresh every minute).

**When ready, you'll see:**
```
Status    | available
Endpoint  | payflow-postgres.xxxx.ap-south-1.rds.amazonaws.com
Port      | 5432
```

**SAVE THE ENDPOINT:**
```cmd
set RDS_ENDPOINT=payflow-postgres.xxxx.ap-south-1.rds.amazonaws.com
echo RDS_ENDPOINT=%RDS_ENDPOINT% >> aws-resources.txt
```

---

## Step 3.3: Initialize Database Schemas

Once RDS is available, we need to create our 4 schemas.
We can't connect directly (it's in private subnet), so we'll do this from EC2 later.

**For now, save this SQL (we'll run it in Part 4 after EC2 is created):**
```sql
-- Run after connecting to RDS from EC2:
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS settlement;
GRANT ALL ON SCHEMA identity TO payflow_admin;
GRANT ALL ON SCHEMA merchant TO payflow_admin;
GRANT ALL ON SCHEMA payment TO payflow_admin;
GRANT ALL ON SCHEMA settlement TO payflow_admin;
```

---

## Step 3.4: Create ElastiCache Redis

### First, create a cache subnet group:
```cmd
aws elasticache create-cache-subnet-group ^
  --cache-subnet-group-name payflow-redis-subnet ^
  --cache-subnet-group-description "Subnets for PayFlow Redis" ^
  --subnet-ids %PRIVATE_SUBNET_1A% %PRIVATE_SUBNET_1B% ^
  --region ap-south-1
```

### Create Redis cluster:
```cmd
aws elasticache create-cache-cluster ^
  --cache-cluster-id payflow-redis ^
  --cache-node-type cache.t3.micro ^
  --engine redis ^
  --num-cache-nodes 1 ^
  --cache-subnet-group-name payflow-redis-subnet ^
  --security-group-ids %DB_SG% ^
  --region ap-south-1
```

**Explanation:**
| Flag | Value | Why |
|------|-------|-----|
| `--cache-cluster-id` | payflow-redis | Unique cluster name |
| `--cache-node-type` | cache.t3.micro | Smallest (0.5 GB RAM, sufficient) |
| `--engine` | redis | Not memcached |
| `--num-cache-nodes` | 1 | Single node (no cluster, saves money) |
| `--security-group-ids` | %DB_SG% | Same security group as RDS (allows from APP) |

**⏳ Takes 3-5 minutes.** Check status:
```cmd
aws elasticache describe-cache-clusters ^
  --cache-cluster-id payflow-redis ^
  --show-cache-node-info ^
  --query "CacheClusters[0].{Status:CacheClusterStatus,Endpoint:CacheNodes[0].Endpoint.Address,Port:CacheNodes[0].Endpoint.Port}" ^
  --output table ^
  --region ap-south-1
```

**When ready:**
```
Status    | available
Endpoint  | payflow-redis.xxxx.0001.aps1.cache.amazonaws.com
Port      | 6379
```

**SAVE:**
```cmd
set REDIS_ENDPOINT=payflow-redis.xxxx.0001.aps1.cache.amazonaws.com
echo REDIS_ENDPOINT=%REDIS_ENDPOINT% >> aws-resources.txt
```

---

## Step 3.5: Create DynamoDB Tables

DynamoDB is always free (25 GB + 25 RCU/WCU). No subnet/VPC needed (managed service).

### Table 1: webhook_events
```cmd
aws dynamodb create-table ^
  --table-name payflow-webhook-events ^
  --attribute-definitions AttributeName=event_id,AttributeType=S AttributeName=merchant_id,AttributeType=S AttributeName=created_at,AttributeType=S ^
  --key-schema AttributeName=event_id,KeyType=HASH AttributeName=created_at,KeyType=RANGE ^
  --global-secondary-indexes "[{\"IndexName\":\"merchant-index\",\"KeySchema\":[{\"AttributeName\":\"merchant_id\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"created_at\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --region ap-south-1
```

### Table 2: routing_metrics
```cmd
aws dynamodb create-table ^
  --table-name payflow-routing-metrics ^
  --attribute-definitions AttributeName=route_id,AttributeType=S AttributeName=time_bucket,AttributeType=S ^
  --key-schema AttributeName=route_id,KeyType=HASH AttributeName=time_bucket,KeyType=RANGE ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --region ap-south-1
```

### Table 3: audit_trail
```cmd
aws dynamodb create-table ^
  --table-name payflow-audit-trail ^
  --attribute-definitions AttributeName=entity_id,AttributeType=S AttributeName=timestamp_action,AttributeType=S ^
  --key-schema AttributeName=entity_id,KeyType=HASH AttributeName=timestamp_action,KeyType=RANGE ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --region ap-south-1
```

### Verify tables created:
```cmd
aws dynamodb list-tables --region ap-south-1
```

**Expected:**
```json
{
    "TableNames": [
        "payflow-audit-trail",
        "payflow-routing-metrics",
        "payflow-webhook-events"
    ]
}
```

---

## Step 3.6: Create SQS Queues

```cmd
aws sqs create-queue --queue-name payflow-payment-events --region ap-south-1
aws sqs create-queue --queue-name payflow-webhook-delivery --region ap-south-1
aws sqs create-queue --queue-name payflow-notification --region ap-south-1
aws sqs create-queue --queue-name payflow-payment-events-dlq --region ap-south-1
```

### Verify:
```cmd
aws sqs list-queues --queue-name-prefix payflow --region ap-south-1
```

---

## Step 3.7: Create SNS Topics

```cmd
aws sns create-topic --name payflow-email-notifications --region ap-south-1
aws sns create-topic --name payflow-sms-notifications --region ap-south-1
```

---

## Step 3.8: Verify Everything

```cmd
echo === RDS ===
aws rds describe-db-instances --db-instance-identifier payflow-postgres --query "DBInstances[0].DBInstanceStatus" --output text --region ap-south-1

echo === Redis ===
aws elasticache describe-cache-clusters --cache-cluster-id payflow-redis --query "CacheClusters[0].CacheClusterStatus" --output text --region ap-south-1

echo === DynamoDB ===
aws dynamodb list-tables --region ap-south-1 --output text

echo === SQS ===
aws sqs list-queues --queue-name-prefix payflow --region ap-south-1 --output text

echo === SNS ===
aws sns list-topics --region ap-south-1 --query "Topics[*].TopicArn" --output text
```

**All should show "available" or list correctly.**

---

## Step 3.9: Save All Connection Details

Add to `aws-resources.txt`:
```
RDS_ENDPOINT=payflow-postgres.xxxx.ap-south-1.rds.amazonaws.com
RDS_PORT=5432
RDS_USERNAME=payflow_admin
RDS_PASSWORD=PayFlow2026SecureDB!
RDS_DATABASE=payflow

REDIS_ENDPOINT=payflow-redis.xxxx.0001.aps1.cache.amazonaws.com
REDIS_PORT=6379

DYNAMODB_REGION=ap-south-1
SQS_REGION=ap-south-1
SNS_REGION=ap-south-1
```

⚠️ **NEVER commit passwords to Git!** Keep aws-resources.txt in .gitignore.

---

## Monthly Cost Check

| Service | Monthly Cost | Status |
|---------|:---:|:---:|
| RDS db.t3.micro | ~$15 | Uses credits |
| ElastiCache cache.t3.micro | ~$12 | Uses credits |
| DynamoDB (5 RCU/5 WCU × 3 tables) | **$0** | Always free! |
| SQS (4 queues) | **$0** | Always free! |
| SNS (2 topics) | **$0** | Always free! |
| **Total from credits this step** | **~$27/month** | |

Running total: VPC ($0) + databases ($27) = $27/month from credits.

---

## Git Commit

```cmd
git add docs/phase15-part3-rds-elasticache-dynamodb.md
git commit -m "Phase 15 Part 3: Database services setup (RDS, ElastiCache, DynamoDB, SQS, SNS)"
```

---

## What We Created

| Service | Type | Endpoint | Cost |
|---------|------|----------|------|
| RDS PostgreSQL | db.t3.micro | payflow-postgres.xxx.rds.amazonaws.com:5432 | ~$15/mo |
| ElastiCache Redis | cache.t3.micro | payflow-redis.xxx.cache.amazonaws.com:6379 | ~$12/mo |
| DynamoDB × 3 tables | On-demand | ap-south-1 region | $0 |
| SQS × 4 queues | Standard | ap-south-1 region | $0 |
| SNS × 2 topics | Standard | ap-south-1 region | $0 |

---

## Next Step

→ Continue to **Phase 15 Part 4: EC2 Instances & Docker Deployment**

In Part 4 we'll:
- Launch 2 EC2 t3.micro instances
- Install Docker on them
- Push our Docker images to ECR
- Deploy all services as Docker containers on EC2
- Connect them to RDS and Redis using the endpoints from this step
