# Environment Setup — AWS Account Setup

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

In this guide, you'll set up an **AWS account** and configure the essential services for PayFlow deployment. AWS provides:
- Production hosting for microservices (ECS/EC2)
- Database hosting (RDS PostgreSQL, ElastiCache, DynamoDB)
- Message queuing (SQS)
- Static file hosting (S3, CloudFront)
- Monitoring and logging (CloudWatch)

---

## 2. Concepts Deep Dive

### Why AWS for PayFlow?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PayFlow AWS Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   INTERNET                                                                   │
│       │                                                                      │
│       ▼                                                                      │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                     Route 53 (DNS)                               │       │
│   │                api.payflow.com → ALB                            │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│       │                                                                      │
│       ▼                                                                      │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                Application Load Balancer (ALB)                   │       │
│   │                  HTTPS termination, routing                      │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│       │                                                                      │
│   ┌───┴───────────────────────────────────────────────────────────────┐     │
│   │                        VPC (Virtual Private Cloud)                │     │
│   │   ┌─────────────────────────────────────────────────────────┐    │     │
│   │   │              Public Subnets (2 AZs)                      │    │     │
│   │   │   ┌─────────┐  ┌─────────┐  ┌─────────┐                │    │     │
│   │   │   │API Gate │  │ Identity│  │ Payment │  ...           │    │     │
│   │   │   │   way   │  │ Service │  │ Service │                │    │     │
│   │   │   └─────────┘  └─────────┘  └─────────┘                │    │     │
│   │   │                   ECS Fargate Tasks                      │    │     │
│   │   └─────────────────────────────────────────────────────────┘    │     │
│   │                                                                   │     │
│   │   ┌─────────────────────────────────────────────────────────┐    │     │
│   │   │              Private Subnets (2 AZs)                     │    │     │
│   │   │   ┌─────────┐  ┌─────────┐  ┌─────────┐                │    │     │
│   │   │   │   RDS   │  │  Redis  │  │DynamoDB │                │    │     │
│   │   │   │PostgreSQL│ │ElastiCac│  │ Tables  │                │    │     │
│   │   │   └─────────┘  └─────────┘  └─────────┘                │    │     │
│   │   └─────────────────────────────────────────────────────────┘    │     │
│   │                                                                   │     │
│   │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │     │
│   │   │     SQS      │  │   CloudWatch │  │     S3       │          │     │
│   │   │   Queues     │  │   Logs/Alarm │  │   Buckets    │          │     │
│   │   └──────────────┘  └──────────────┘  └──────────────┘          │     │
│   └───────────────────────────────────────────────────────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### AWS Services Used in PayFlow

| Service | Purpose | Sprint |
|---------|---------|--------|
| **VPC** | Network isolation | Sprint 1 |
| **EC2/ECS** | Run microservices | Sprint 1+ |
| **RDS** | PostgreSQL database | Sprint 1 |
| **ElastiCache** | Redis for caching/sessions | Sprint 3 |
| **DynamoDB** | Webhook events storage | Sprint 7 |
| **SQS** | Message queues | Sprint 7 |
| **S3** | Static file storage | Sprint 10 |
| **CloudFront** | CDN for static files | Sprint 10 |
| **CloudWatch** | Monitoring, logging, alerts | Sprint 11 |
| **Route 53** | DNS management | Sprint 12 |
| **ACM** | SSL certificates | Sprint 12 |
| **IAM** | Access management | All |

### AWS Free Tier

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AWS Free Tier (12 months)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   SERVICE           FREE TIER LIMIT              PAYFLOW USAGE              │
│   ─────────────────────────────────────────────────────────────────────     │
│   EC2               750 hrs/month t2.micro       Dev environment            │
│   RDS               750 hrs/month db.t3.micro    Dev database               │
│   ElastiCache       750 hrs/month cache.t3.micro Dev Redis                  │
│   S3                5 GB storage                 Static files               │
│   DynamoDB          25 GB storage                Webhook events             │
│   SQS               1 million requests/month     Message queues             │
│   CloudWatch        10 custom metrics            Basic monitoring           │
│   Lambda            1 million requests/month     Optional functions         │
│                                                                              │
│   ⚠️  IMPORTANT:                                                            │
│   • Free Tier only for NEW AWS accounts                                     │
│   • 12 months from signup                                                   │
│   • Exceeding limits = CHARGES                                              │
│   • Set up billing alerts!                                                  │
│                                                                              │
│   ESTIMATED MONTHLY COST (after free tier):                                 │
│   • Development:  $20-50/month                                              │
│   • Production:   $200-500/month                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

| Requirement | Status |
|-------------|--------|
| Email address | For AWS account |
| Phone number | For verification |
| Credit/Debit card | Required (won't charge if in free tier) |
| Government ID | May be required for verification |

---

## 4. Step-by-Step Installation

### Step 4.1: Create AWS Account

1. Go to: https://aws.amazon.com/
2. Click **"Create an AWS Account"** (top right)
3. Enter:
   - Email address
   - AWS account name: "PayFlow Development" (or your name)
   - Click **"Verify email address"**
4. Check email for verification code
5. Enter code and continue

---

### Step 4.2: Contact Information

1. Select **"Personal"** account (or Business if applicable)
2. Enter your details:
   - Full name
   - Phone number
   - Country
   - Address
3. Accept AWS Customer Agreement
4. Click **"Continue"**

---

### Step 4.3: Payment Information

1. Enter credit/debit card details
2. Enter billing address
3. Click **"Verify and Continue"**

**Note:** AWS may charge $1 temporarily for verification (refunded immediately).

---

### Step 4.4: Identity Verification

1. Choose verification method: **Text message (SMS)** or **Voice call**
2. Enter your phone number
3. Enter the verification code received
4. Click **"Continue"**

---

### Step 4.5: Select Support Plan

1. Select **"Basic support - Free"**
   - Sufficient for learning and development
   - Upgrade later if needed for production
2. Click **"Complete sign up"**

---

### Step 4.6: Wait for Activation

1. Account activation takes a few minutes
2. You'll receive an email when ready
3. Sign in at: https://console.aws.amazon.com/

---

## 5. Essential AWS Configuration

### Step 5.1: Secure Root Account

**Root account = full access. Protect it!**

1. Sign in as root user
2. Go to: **My Account** (top right) → **My Security Credentials**
3. Set up **MFA (Multi-Factor Authentication)**:
   - Click **"Assign MFA device"**
   - Choose **"Authenticator app"**
   - Scan QR code with phone (Google Authenticator, Authy, etc.)
   - Enter two consecutive codes
   - Click **"Add MFA"**

---

### Step 5.2: Create IAM Admin User

**Never use root account for daily work!**

1. Search for **"IAM"** in AWS Console
2. Go to **Users** → **Add users**
3. User details:
   - User name: `payflow-admin`
   - Select: ✅ **Provide user access to the AWS Management Console**
   - Console password: **Autogenerated** or custom
   - ✅ **Users must create a new password at next sign-in** (optional)
4. Click **"Next"**
5. Permissions:
   - Select **"Attach policies directly"**
   - Search and check: ✅ **AdministratorAccess**
6. Click **"Next"** → **"Create user"**
7. **SAVE** the sign-in URL, username, and password!
8. Set up MFA for this user too (same process)

---

### Step 5.3: Create Access Keys for CLI

1. In IAM, go to your user (`payflow-admin`)
2. **Security credentials** tab
3. **Access keys** → **Create access key**
4. Use case: **Command Line Interface (CLI)**
5. Check confirmation → **Next**
6. Description: "PayFlow CLI"
7. **Create access key**
8. **DOWNLOAD** the .csv file (contains Access Key ID and Secret)

**⚠️ WARNING:** This is the ONLY time you can view the secret key. Save it securely!

---

### Step 5.4: Install AWS CLI

1. Download: https://awscli.amazonaws.com/AWSCLIV2.msi
2. Run the installer
3. Accept defaults
4. Complete installation

**Verify installation:**

```powershell
# Check AWS CLI version
aws --version
```

**Expected Output:**
```
aws-cli/2.x.x Python/3.x.x Windows/10 exe/AMD64
```

---

### Step 5.5: Configure AWS CLI

```powershell
# Configure AWS CLI with your credentials
aws configure
```

**Prompts:**
```
AWS Access Key ID [None]: <your-access-key-id>
AWS Secret Access Key [None]: <your-secret-access-key>
Default region name [None]: us-east-1
Default output format [None]: json
```

**Verify configuration:**

```powershell
# Test AWS CLI
aws sts get-caller-identity
```

**Expected Output:**
```json
{
    "UserId": "AIDAXXXXXXXXXXXXXXXXX",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/payflow-admin"
}
```

---

### Step 5.6: Set Up Billing Alerts

**CRITICAL: Avoid surprise charges!**

1. Go to: **Billing and Cost Management** (search in console)
2. **Billing preferences** (left sidebar)
3. Enable:
   - ✅ Receive PDF Invoice By Email
   - ✅ Receive Free Tier Usage Alerts
   - ✅ Receive CloudWatch Billing Alerts
4. Click **"Update"**

**Create Budget Alert:**

1. **Budgets** (left sidebar)
2. **Create a budget**
3. Select: **Use a template** → **Zero spend budget**
4. Budget name: "PayFlow Zero Spend Alert"
5. Email: Your email
6. **Create budget**

This alerts you if ANY charges occur.

---

## 6. Verification

### Test AWS Console Access

1. Sign out of root account
2. Go to your IAM user sign-in URL
3. Sign in with `payflow-admin` credentials
4. Verify you can access services

### Test AWS CLI

```powershell
# List S3 buckets (empty is OK)
aws s3 ls

# List VPCs
aws ec2 describe-vpcs

# List RDS instances (empty is OK)
aws rds describe-db-instances
```

### Verify Region

```powershell
# Check configured region
aws configure get region
```

**Expected:** `us-east-1` (or your chosen region)

---

## 7. Understanding AWS Concepts

### AWS Regions and Availability Zones

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AWS Global Infrastructure                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   REGION (e.g., us-east-1 "N. Virginia")                                    │
│   └── Availability Zone 1 (us-east-1a)                                      │
│       └── Data Center                                                       │
│   └── Availability Zone 2 (us-east-1b)                                      │
│       └── Data Center                                                       │
│   └── Availability Zone 3 (us-east-1c)                                      │
│       └── Data Center                                                       │
│                                                                              │
│   WHY MULTIPLE AZs?                                                         │
│   • High Availability: If one AZ fails, others continue                     │
│   • Disaster Recovery: Data replicated across AZs                           │
│   • Low Latency: AZs connected by high-speed network                        │
│                                                                              │
│   RECOMMENDED REGIONS:                                                       │
│   • us-east-1: Most services, lowest prices                                 │
│   • us-west-2: Good alternative                                             │
│   • eu-west-1: For European users                                           │
│   • ap-south-1: For Indian users                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### IAM (Identity and Access Management)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           IAM Concepts                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ROOT ACCOUNT                                                               │
│   │   • Full access to everything                                           │
│   │   • Only use for billing and IAM setup                                  │
│   │   • Protect with MFA!                                                   │
│   │                                                                          │
│   └── IAM USERS (e.g., payflow-admin)                                       │
│       │   • Individual people                                               │
│       │   • Own credentials (password, access keys)                         │
│       │   • Assigned permissions via policies                               │
│       │                                                                      │
│       └── IAM POLICIES                                                      │
│           │   • JSON documents defining permissions                         │
│           │   • AdministratorAccess = full access                          │
│           │   • Can create custom policies                                  │
│           │                                                                  │
│           └── IAM ROLES                                                     │
│               • Assumed by services (not users)                             │
│               • EC2 instance role                                           │
│               • ECS task role                                               │
│               • Lambda execution role                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### AWS CLI Common Commands

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AWS CLI Command Reference                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GENERAL:                                                                   │
│   aws configure                    # Set up credentials                     │
│   aws sts get-caller-identity      # Verify who you are                    │
│   aws configure list               # Show current config                    │
│                                                                              │
│   S3 (Storage):                                                              │
│   aws s3 ls                        # List buckets                           │
│   aws s3 mb s3://bucket-name       # Create bucket                          │
│   aws s3 cp file.txt s3://bucket/  # Upload file                           │
│   aws s3 sync ./folder s3://bucket/# Sync folder                           │
│                                                                              │
│   EC2 (Compute):                                                             │
│   aws ec2 describe-instances       # List EC2 instances                    │
│   aws ec2 describe-vpcs            # List VPCs                             │
│   aws ec2 describe-subnets         # List subnets                          │
│                                                                              │
│   RDS (Database):                                                            │
│   aws rds describe-db-instances    # List databases                        │
│                                                                              │
│   ECS (Containers):                                                          │
│   aws ecs list-clusters            # List ECS clusters                     │
│   aws ecs list-services            # List services in cluster              │
│                                                                              │
│   CloudWatch (Monitoring):                                                   │
│   aws logs describe-log-groups     # List log groups                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Root Account** | Only for initial setup, protect with MFA |
| **IAM User** | Daily work account, least privilege |
| **Access Keys** | For CLI/SDK, keep secret! |
| **Region** | Choose one and stick with it (us-east-1) |
| **Free Tier** | 12 months, set billing alerts |
| **Billing Alerts** | Critical to avoid surprise charges |

---

## 9. Q&A / Troubleshooting

### "Access Denied" in Console

**Fix:**
1. Check you're signed in as IAM user, not root
2. Verify user has correct permissions
3. Check you're in the right region

### AWS CLI "Unable to locate credentials"

**Fix:**
```powershell
# Re-run configure
aws configure

# Or check credentials file
type ~/.aws/credentials
```

### "The security token included in the request is invalid"

**Fix:**
1. Regenerate access keys in IAM
2. Update AWS CLI config
3. Check for typos in access key

### Unexpected charges

**Fix:**
1. Check **Cost Explorer** in Billing
2. Identify the service causing charges
3. Delete or stop unused resources
4. Contact AWS Support if billing error

### "You are not authorized to perform this operation"

**Fix:**
1. Check IAM policy attached to your user
2. May need additional permissions
3. Verify region is correct

---

## 10. Next Steps

**Continue to:** [10-verification-checklist.md](./10-verification-checklist.md)

In the final environment setup guide, you'll verify everything is working together.

**What you've accomplished:**
- ✅ Created AWS account
- ✅ Secured root account with MFA
- ✅ Created IAM admin user
- ✅ Installed and configured AWS CLI
- ✅ Set up billing alerts
- ✅ Understand AWS concepts

---

**End of AWS Account Setup**
