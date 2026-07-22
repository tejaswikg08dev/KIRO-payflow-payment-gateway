# Phase 1 — Part 4: AWS Free Tier Plan & Cost Strategy

> This document explains AWS from scratch — what it is, how the new Free Tier
> works (changed in July 2025), which services we use, why, and how to avoid
> unexpected charges. Read this fully before creating an AWS account.

---

## 1. What Is AWS? (Quick Refresher)

AWS (Amazon Web Services) is a cloud platform that provides servers, databases,
storage, and 200+ other services over the internet. Instead of buying a physical
server (₹1-5 lakh), you rent one from AWS for ₹600-5000/month.

**Why we use AWS for this project:**
- Real companies deploy payment systems on AWS (Stripe, Razorpay use it)
- Interview questions specifically ask about AWS services
- $200 free credits means we don't pay anything
- Same services used in production (RDS, ElastiCache, etc.)

**What we are NOT doing:**
- We are NOT buying any server
- We are NOT paying monthly bills
- We use ONLY free services + $200 credits that AWS gives us for free

---

## 2. AWS Free Tier — Complete Explanation (New Rules Since July 2025)

### 2.1 What Changed?

Before July 2025, AWS gave 12 months of free tier. **This no longer exists for new accounts.**

AWS now has a completely new system. Here's how it works:

```
BEFORE (Old system — accounts created before July 2025):
├── 12 months of free EC2 (750 hrs/month)
├── 12 months of free RDS (750 hrs/month)
├── 12 months of free ElastiCache
├── Always-free services (DynamoDB, SQS, etc.)
└── After 12 months → start paying

NOW (New system — accounts created after July 2025):
├── Choose: "Free Plan" or "Paid Plan"
├── Get $100 credits immediately at signup
├── Earn $100 more by trying services (total $200)
├── Free Plan lasts 6 months, then ACCOUNT CLOSES
├── Always-free services still work forever
└── $200 credits can be used on ANY service
```

### 2.2 Free Plan vs Paid Plan — Which to Choose?

| Question | Free Plan | Paid Plan |
|----------|-----------|-----------|
| How long does it last? | **6 months** | No expiry |
| What happens after? | **Account closes** (data deleted!) | You start paying |
| Credits | $200 same as Paid | $200 same as Free |
| Can I get charged? | **NO** — account closes before bills | YES — you pay after credits |
| Risk of surprise bill? | **Zero risk** | YES — if you forget to delete resources |
| Best for? | **Learning/demo (US!)** | Production use |

**Our choice: FREE PLAN**

Why? Because:
- We're building a learning project
- Zero risk of surprise charges
- After 6 months, account closes (no bill ever)
- We'll tear down resources way before 6 months
- If we need more time, we create a new account

### 2.3 How the $200 Credits Work

```
STEP 1: Create AWS account (choose Free Plan)
         → You immediately get $100 in credits

STEP 2: Try these services (each gives extra credits):
         ├── Launch an EC2 instance → earn $20
         ├── Create an RDS database → earn $20
         ├── Try AWS Lambda → earn $20
         ├── Try Amazon Bedrock (AI) → earn $20
         └── Set up AWS Budgets → earn $20
         → Total earned: up to $100 more

STEP 3: Now you have $200 total credits
         → These credits PAY for any AWS service you use
         → It's like a gift card — use until it runs out

STEP 4: Credits expire when:
         ├── 6 months pass (Free Plan closes), OR
         └── Credits are fully used up (whichever comes FIRST)
```

---

## 3. Three Types of AWS Services (Explained Simply)

### Type 1: Always Free (Never Costs Money — Even After 6 Months)

These services have a "forever free" limit. As long as you stay under the limit,
you pay $0 — today, next year, forever. No credits needed.

**Think of it like:** Free unlimited texting on your phone plan — always included.

| Service | What It Does (Simple) | Free Limit | Our Use |
|---------|----------------------|-----------|---------|
| **DynamoDB** | NoSQL database (key-value store) | 25 GB storage, 25 reads/sec, 25 writes/sec | Webhook event logs, audit trail |
| **SQS** | Message queue (service A sends message, service B reads later) | 1 million messages/month | Payment events, notification queue |
| **SNS** | Send emails and SMS | 1 million publishes/month | Payment alerts, fraud alerts |
| **S3** | File storage (like Google Drive for code) | 5 GB storage | React frontend files, PDF reports |
| **CloudFront** | CDN (makes website fast globally) | 1 TB data/month | Serve React app from nearest edge |
| **CloudWatch** | Monitoring (see logs, set alarms) | 10 metrics, 5 GB logs | Monitor all services |
| **Lambda** | Run code without a server | 1 million runs/month | Cron job triggers (optional) |
| **Cognito** | User login system | 50,000 users/month | Optional (we build our own JWT auth) |

**Key point:** These 8 services cost us $0. We can use them forever.
Our webhook-service, notification-service, frontend hosting, and monitoring
ALL run on these free services.

### Type 2: Credit-Based (Uses $200 Credits)

These services cost money, but our $200 credits pay for them.
Once credits run out (or 6 months pass), these services stop.

**Think of it like:** A prepaid phone plan — ₹200 balance, use until empty.

| Service | What It Does (Simple) | Monthly Cost | Why We Need It |
|---------|----------------------|-------------|---------------|
| **EC2** (t3.micro) | Virtual server (like a remote computer) | ~$8.50/instance | Run our Java services in Docker |
| **RDS** (db.t3.micro) | Managed PostgreSQL database | ~$15/month | Store payments, merchants, users |
| **ElastiCache** (cache.t3.micro) | Managed Redis (super-fast cache) | ~$12/month | Idempotency, rate limiting, caching |
| **ALB** | Load balancer (splits traffic between servers) | ~$16/month | Distribute requests across EC2s |

**Total monthly cost:** ~$52-64/month
**$200 credits last:** ~3-4 months of continuous running

### Type 3: Trials (Short-term — 30-60 days)

Some services have a brief trial period. We don't rely on these.

| Service | Trial | We Use? |
|---------|-------|---------|
| SageMaker | 2 months free | No |
| Redshift | 2 months free | No |
| Bedrock | Covered by credits | Optional (AI fraud) |

---

## 4. Exactly Which Services We Use (Complete List)

### 4.1 Development Phase (Phase 1-12) — $0 Cost

During development, everything runs on your laptop using Docker:

```
YOUR LAPTOP (Docker Compose):
│
├── PostgreSQL (Docker container — postgres:15)
│   └── Replaces: AWS RDS
│   └── How: docker run -d --name pg -e POSTGRES_PASSWORD=secret -p 5432:5432 postgres:15
│
├── Redis (Docker container — redis:7)
│   └── Replaces: AWS ElastiCache
│   └── How: docker run -d --name redis -p 6379:6379 redis:7
│
├── DynamoDB Local (Docker container — amazon/dynamodb-local)
│   └── Same as: Real DynamoDB (free anyway)
│   └── How: docker run -d --name dynamo -p 8000:8000 amazon/dynamodb-local
│
├── LocalStack (Docker container — localstack/localstack)
│   └── Simulates: SQS, SNS, S3 locally
│   └── How: docker run -d --name localstack -p 4566:4566 localstack/localstack
│
├── All 11 Java microservices (each as Docker container)
│   └── How: docker-compose up (starts everything)
│
└── React frontends (npm run dev — runs locally)
    └── How: cd frontend-dashboard && npm run dev

TOTAL COST: $0 (everything on your computer)
DURATION: Months — no time limit
```

### 4.2 AWS Deployment Phase (Phase 15) — Uses Credits

When ready to deploy to real AWS for demo:

```
AWS ACCOUNT:
│
├── EC2 Instance #1 (t3.micro — 2 vCPU, 1 GB RAM)
│   ├── Runs: API Gateway, Identity Service, Merchant Service, Eureka, Config
│   ├── Cost: ~$8.50/month
│   └── Deploy: Docker Compose on this machine
│
├── EC2 Instance #2 (t3.micro — 2 vCPU, 1 GB RAM)
│   ├── Runs: Payment Service, Routing Service, Settlement, Webhook, Notification, Bank Sim
│   ├── Cost: ~$8.50/month
│   └── Deploy: Docker Compose on this machine
│
├── RDS PostgreSQL (db.t3.micro — 2 vCPU, 1 GB RAM, 20 GB SSD)
│   ├── Runs: PostgreSQL 15 (all schemas)
│   ├── Cost: ~$15/month
│   └── Managed: AWS handles backups, patches, monitoring
│
├── ElastiCache Redis (cache.t3.micro — 1 GB RAM)
│   ├── Runs: Redis 7 (idempotency, cache, rate limiting)
│   ├── Cost: ~$12/month
│   └── Managed: AWS handles failover, monitoring
│
├── ALB (Application Load Balancer)
│   ├── Purpose: Splits traffic between EC2 #1 and #2
│   ├── Cost: ~$16/month
│   └── Also: SSL certificate (free with ACM)
│
├── DynamoDB (Always Free — no credits used)
├── SQS (Always Free — no credits used)
├── SNS (Always Free — no credits used)
├── S3 + CloudFront (Always Free — frontend hosting)
└── CloudWatch (Always Free — monitoring)

TOTAL FROM CREDITS: ~$60/month
PLAN: Deploy for 2-3 weeks → use ~$30-50 of credits → tear down
```

---

## 5. Step-by-Step: How to Create AWS Account

> Do this ONLY when you reach Phase 15 (deployment).
> For Phase 1-12, you don't need an AWS account at all.

### Step 1: Go to AWS Signup

Open browser → go to **https://aws.amazon.com/** → click **"Create an AWS Account"**

### Step 2: Enter Account Details

```
Root user email: your-email@gmail.com (use a personal email you check regularly)
AWS account name: "payflow-demo" (just a label)
Password: Strong password (save it somewhere safe!)
```

### Step 3: Choose Account Plan

You'll see two options:
```
┌─────────────────────────────┐    ┌─────────────────────────────┐
│         FREE PLAN           │    │         PAID PLAN           │
│                             │    │                             │
│  ✅ $200 credits            │    │  $200 credits               │
│  ✅ 6 months access         │    │  Unlimited access           │
│  ✅ No charges ever         │    │  ⚠️ Charged after credits    │
│  ✅ Account closes safely   │    │  Need to manually teardown  │
│                             │    │                             │
│  ──── CHOOSE THIS ────      │    │                             │
└─────────────────────────────┘    └─────────────────────────────┘
```

Click **"Free Plan"**

### Step 4: Enter Payment Method

```
AWS asks for credit/debit card. DON'T WORRY:
├── They verify the card is real (₹2 temporary charge, refunded)
├── On Free Plan, you will NEVER be charged
├── Account closes automatically after 6 months
└── Use any Visa/Mastercard debit card
```

### Step 5: Identity Verification

```
├── Enter phone number
├── Receive OTP via SMS
├── Enter OTP
└── Verified!
```

### Step 6: Select Support Plan

```
Choose: "Basic support — Free"
(Don't choose Developer/Business — those cost money)
```

### Step 7: Done!

```
You now have:
├── AWS account ready
├── $100 credits immediately available
├── Can earn $100 more by trying services
└── 6 months to use everything
```

---

## 6. Budget Alerts — Set Up IMMEDIATELY After Account Creation

This is CRITICAL. Even on Free Plan, you want to track spending.

### Step-by-Step:

**Step 1:** Log into AWS Console → search "Billing" → click "AWS Billing and Cost Management"

**Step 2:** In left menu → click "Budgets"

**Step 3:** Click "Create budget"

**Step 4:** Select "Use a template" → "Monthly cost budget"

**Step 5:** Fill in:
```
Budget name: PayFlow Monthly Budget
Budget amount: $50
Email recipients: your-email@gmail.com
```

**Step 6:** Click "Create budget"

Now you'll get an email alert if spending approaches $50/month.

### Also Enable Free Tier Alerts:

**Step 1:** Go to Billing → "Billing preferences" (left menu)

**Step 2:** Check the box: "Receive AWS Free Tier usage alerts"

**Step 3:** Enter your email → Save

Now you'll get an alert if you're approaching any free tier limit.

---

## 7. Money-Saving Tips

| Tip | How | Saves |
|-----|-----|-------|
| **Stop EC2 when not using** | AWS Console → EC2 → Select → Stop (not terminate) | ~$0.012/hr per instance |
| **Stop RDS when not using** | AWS Console → RDS → Modify → Stop temporarily | ~$0.02/hr |
| **Don't create NAT Gateway** | Use EC2 in public subnet instead | $32/month avoided! |
| **Use t3.micro only** | Never select a bigger instance | Stay in budget |
| **Delete ALB last** | Only create ALB when demoing | ~$16/month |
| **Don't enable Multi-AZ** | Single-AZ for RDS is fine (not production) | Saves ~$15/month |
| **Use gp2 storage (20GB)** | Don't increase RDS storage | Saves storage costs |

### The Most Common Surprise Charge (AVOID THIS):

```
⚠️ NAT GATEWAY = $32/month minimum!

Many tutorials tell you to create a NAT Gateway for private subnets.
DON'T DO THIS. It costs $0.045/hour × 24 × 30 = $32.40/month.

Instead: Put EC2 instances in a PUBLIC subnet with security groups.
For a demo project, this is fine. We're not handling real card data.
```

---

## 8. What If Credits Run Out?

```
ON FREE PLAN:
├── Credits run out → Account closes → All resources deleted
├── You cannot be charged
├── Your data is gone (but code is on GitHub)
└── Create a new account if you need more time

ON PAID PLAN:
├── Credits run out → AWS starts charging your card
├── You MUST delete resources manually
├── Otherwise monthly bill continues
└── This is why we chose Free Plan (safer)
```

---

## 9. Quick Reference: Our Services & Costs

| Service | Always Free? | Our Usage | Monthly Cost |
|---------|:---:|---|---|
| DynamoDB | ✅ | Webhook logs, audit, routing metrics | $0 |
| SQS | ✅ | Payment events, webhook queue, notify queue | $0 |
| SNS | ✅ | Email/SMS alerts | $0 |
| S3 | ✅ | Frontend hosting, settlement PDFs | $0 |
| CloudFront | ✅ | CDN for frontend | $0 |
| CloudWatch | ✅ | Logs, metrics, alarms | $0 |
| EC2 × 2 | ❌ | Java services in Docker | ~$17 (credits) |
| RDS | ❌ | PostgreSQL database | ~$15 (credits) |
| ElastiCache | ❌ | Redis cache | ~$12 (credits) |
| ALB | ❌ | Load balancer | ~$16 (credits) |
| **TOTAL** | | | **~$60/month** |

---

## 10. Interview Questions This Document Answers

1. **"How do you handle cloud costs for your project?"**
   → "I used AWS Free Plan with $200 credits. I designed the architecture to maximize always-free services (DynamoDB for event logs, SQS for messaging, S3+CloudFront for frontend hosting). Credit-consuming services (EC2, RDS, ElastiCache) are only deployed during demo periods and stopped when not in use."

2. **"What AWS services do you use and why?"**
   → EC2 for compute, RDS for relational data (ACID for payments), ElastiCache for caching (idempotency keys), DynamoDB for high-write event logs, SQS for async decoupling, SNS for notifications, S3+CloudFront for frontend.

3. **"Why not just use DynamoDB for everything?"**
   → Payment data requires ACID transactions (debit one account, credit another atomically). DynamoDB doesn't support multi-table transactions well. PostgreSQL handles this with BEGIN/COMMIT.

4. **"How would you scale this on AWS in production?"**
   → Auto-scaling groups for EC2, RDS read replicas, ElastiCache cluster, ALB across AZs, DynamoDB auto-scales automatically, SQS auto-scales. For >10K TPS, move to ECS/EKS with container orchestration.

---

## Next Step

→ Continue to **`phase1-part5-development-environment-setup.md`**
