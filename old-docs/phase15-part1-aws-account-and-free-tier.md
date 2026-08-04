# Hands-On Guide — Phase 15 Part 1: AWS Account Setup and Free Tier

## Goal

By the end of Part 1 you will have:
- AWS account created (Free Plan — new 2025 model)
- Root account secured with MFA (Multi-Factor Authentication)
- IAM user created for daily use (never use root for daily work)
- Access keys generated (needed for AWS CLI and CI/CD)
- Budget alert set up ($50 budget to avoid surprise charges)
- AWS CLI installed and configured on your laptop
- Understanding of $200 credits and how to earn them
- Understanding of what costs money vs what's always free

## Prerequisites

- Email address (not already used for AWS)
- Credit/debit card (Visa/Mastercard — required for verification, won't be charged on Free Plan)
- Phone number (for SMS verification)
- 30-45 minutes of time

---

## Understanding AWS Free Tier (July 2025 Onwards — Read First)

### What Changed?

```
OLD MODEL (before July 2025):
├── 12 months of free EC2, RDS, etc.
├── No credits
├── After 12 months → start paying
└── Account stays open forever

NEW MODEL (July 2025 onwards — THIS IS WHAT WE USE):
├── Choose: "Free Plan" or "Paid Plan" at signup
├── Get $100 credits immediately
├── Earn $100 more by trying services (total $200)
├── Free Plan lasts 6 months, then ACCOUNT CLOSES
├── Always-free services continue forever on both plans
└── Zero risk of surprise charges on Free Plan!
```

### Free Plan vs Paid Plan

```
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│         FREE PLAN                │    │         PAID PLAN                │
│  (CHOOSE THIS FOR OUR PROJECT)  │    │                                  │
│                                  │    │                                  │
│  ✅ $200 credits                 │    │  $200 credits                    │
│  ✅ 6 months access              │    │  Unlimited access                │
│  ✅ No charges EVER              │    │  ⚠️ Charged after credits run out │
│  ✅ Account closes safely        │    │  Need to manually delete stuff   │
│  ✅ Can't accidentally get billed│    │  Can get surprise bills          │
│                                  │    │                                  │
│  After 6 months:                 │    │  After credits:                  │
│  Account closes, data deleted    │    │  Card gets charged monthly       │
│  (our code is on GitHub anyway)  │    │                                  │
└─────────────────────────────────┘    └─────────────────────────────────┘
```

### Things That Are ALWAYS Free (No Credits Needed)

```
These services have permanent free tiers that NEVER expire:

✅ DynamoDB: 25 GB storage + 25 read/write capacity units
✅ SQS: 1 million messages/month
✅ SNS: 1 million publishes/month
✅ S3: 5 GB storage
✅ CloudFront: 1 TB data transfer/month
✅ CloudWatch: 10 custom metrics + 5 GB log storage
✅ Lambda: 1 million invocations/month
✅ Cognito: 50,000 monthly active users

Our webhook-service, notification-service, frontend hosting, and monitoring
ALL run on these services = $0 forever.
```

### Things That Use $200 Credits

```
These cost money but our credits cover them:

EC2 t3.micro (×2): ~$17/month (run all Docker containers)
RDS db.t3.micro: ~$15/month (PostgreSQL database)
ElastiCache cache.t3.micro: ~$12/month (Redis)
ALB: ~$16/month (load balancer)
─────────────────────────────
TOTAL: ~$60/month
$200 credits ÷ $60/month = ~3.3 months of running

STRATEGY: Deploy for 2-3 weeks for demo, then tear down. Credits last easily.
```

### ⚠️ Things to AVOID (Will Eat Credits Fast!)

```
❌ NAT Gateway = $32/month minimum!! (DO NOT CREATE THIS)
   → Instead: Put EC2 in public subnet with security groups
   
❌ Multiple EC2 instances bigger than t3.micro
   → Always use t3.micro (smallest)
   
❌ RDS Multi-AZ = doubles the cost
   → Use Single-AZ (fine for demo project)
   
❌ Elastic IP not attached to running instance = $3.60/month
   → Always attach to EC2 or release it
   
❌ EBS volumes left after terminating EC2
   → Check and delete orphaned volumes

✅ SAFE: Follow this guide exactly = stay within budget
```

---

## Step 1.1: Create AWS Account

### Go to AWS Signup Page

```
1. Open your browser
2. Go to: https://aws.amazon.com/
3. Click the orange button: "Create an AWS Account"
   (or "Create a Free Account" — same thing)
```

### Page 1: Email and Account Name

```
Root user email address: your-personal-email@gmail.com
  → Use an email you CHECK REGULARLY (billing alerts go here!)
  → Don't use a work email (might lose access if you leave)
  
AWS account name: payflow-demo
  → Just a label (can be anything, doesn't affect functionality)
  
Click: "Verify email address"
  → Check your inbox for a 6-digit verification code
  → Enter the code on the AWS page
```

### Page 2: Root User Password

```
Create a password:
  → Minimum 8 characters
  → Use a STRONG password (16+ chars, mix letters/numbers/symbols)
  → Example: PayFl0w-AWS-2026!Demo
  → SAVE THIS in a password manager (LastPass, 1Password, or a safe note)
  
This is the ROOT account — it has FULL control over everything.
If someone gets this password + your email → they own your account.
```

### Page 3: Choose Account Plan

```
You'll see TWO options:

┌──────────────┐     ┌──────────────┐
│  Free Plan   │     │  Paid Plan   │
│  (6 months)  │     │  (unlimited) │
└──────────────┘     └──────────────┘

→ Click "Free Plan"

WHY Free Plan?
├── Zero risk of charges (account closes, not bills)
├── $200 credits same as Paid Plan
├── We'll tear down resources way before 6 months
└── If we need more time, create new account (free again!)
```

### Page 4: Contact Information

```
Account type: Personal (not Business)
Full name: Your Name
Phone number: Your mobile (+91XXXXXXXXXX)
Country: India
Address: Your address
State: Your state
City: Your city
Postal code: Your PIN

Click "Continue"
```

### Page 5: Payment Information

```
Credit or debit card number: Your Visa/Mastercard
Expiration date: MM/YY
Cardholder name: YOUR NAME

⚠️ DON'T WORRY: On Free Plan, you will NEVER be charged!
  → AWS verifies the card is real (₹2 temporary hold, refunded)
  → The card is there ONLY for identity verification
  → Free Plan = account closes after 6 months, no bill ever

Click "Verify and Continue"
```

### Page 6: Identity Verification

```
Verification method: Text message (SMS)
Country code: India (+91)
Phone number: Your mobile number

Click "Send SMS"
  → You'll receive a 4-digit code via SMS
  → Enter it on the page
  → Click "Verify"
```

### Page 7: Support Plan

```
Choose: "Basic support - Free" ← SELECT THIS

DO NOT select:
  ❌ Developer ($29/month)
  ❌ Business ($100/month)
  ❌ Enterprise ($15,000/month)

Click "Complete sign up"
```

### Page 8: Done!

```
"Congratulations! Your AWS account is being activated."
  → Wait 1-5 minutes for activation email
  → Then you can sign in at: https://console.aws.amazon.com

You now have:
├── AWS account ready
├── $100 credits immediately available
└── 6 months to use everything
```

---

## Step 1.2: Sign In and Set Your Region

```
1. Go to: https://console.aws.amazon.com
2. Sign in type: "Root user"
3. Enter your email
4. Enter your password
5. You're in the AWS Console!

SET REGION (VERY IMPORTANT):
6. Look at the TOP-RIGHT corner
7. You'll see a region name (might say "Ohio" or "N. Virginia")
8. Click on it
9. Select: "Asia Pacific (Mumbai) ap-south-1"

WHY ap-south-1 (Mumbai)?
├── Lowest latency for India
├── All services we need are available
├── Free tier works the same in any region
└── KEEP EVERYTHING IN ONE REGION (services in different regions can't talk!)

⚠️ ALWAYS check you're in ap-south-1 before creating anything!
   If you accidentally create EC2 in us-east-1 and RDS in ap-south-1,
   they can't communicate without internet (expensive + slow).
```

---

## Step 1.3: Secure Root Account with MFA

**Why?** Root account has UNLIMITED power. If someone gets your password, they can:
- Create $50,000 in resources (you're liable!)
- Delete everything
- Change your email/password (lock you out)
- MFA = even with stolen password, they need your PHONE too

```
1. Top-right: Click your account name → "Security credentials"
   (Or search "IAM" in the search bar → Click IAM → Dashboard)
   
2. You'll see: "Root user MFA not enabled" (security warning)

3. Click "Assign MFA device" (or "Enable MFA")

4. Device name: "MyPhone"

5. MFA device type: "Authenticator app"
   Click "Next"

6. On your PHONE:
   a. Open Google Play Store (Android) or App Store (iPhone)
   b. Search and install: "Google Authenticator"
      (or "Microsoft Authenticator" — either works)
   c. Open the app
   d. Tap "+" to add a new account
   e. Choose "Scan QR code"
   f. Point camera at the QR code on your AWS screen
   g. The app shows a 6-digit code that changes every 30 seconds

7. Back on AWS screen:
   Enter MFA code 1: (current 6-digit code from app)
   Wait for it to change (30 seconds)
   Enter MFA code 2: (next 6-digit code from app)
   
8. Click "Assign MFA"

DONE! Now login requires: password + 6-digit code from your phone app
```

---

## Step 1.4: Create IAM User (For Daily Use)

**Why?** NEVER use root for daily work. Create a separate user.
If this user's credentials are stolen, damage is limited.
Root = only for billing and account-wide settings.

```
1. AWS Console → Search "IAM" → Click "IAM"

2. Left sidebar: "Users" → Click "Create user"

3. User name: payflow-admin
   Check: ✅ "Provide user access to the AWS Management Console"
   Select: "I want to create an IAM user"
   Custom password: Choose-A-Strong-Password-2026!
   Uncheck: ☐ "Users must create a new password at next sign-in"
   Click "Next"

4. Permissions:
   Select: "Attach policies directly"
   Search: "AdministratorAccess"
   Check: ✅ AdministratorAccess
   Click "Next"
   
   (In production you'd give only needed permissions.
   For our learning project, AdminAccess is fine.)

5. Review and create:
   Click "Create user"

6. SAVE THE SIGN-IN URL:
   You'll see: "https://123456789012.signin.aws.amazon.com/console"
   → Bookmark this! Use this URL to login as IAM user (not root)
   → The 12-digit number is your AWS account ID

7. From now on:
   → Login with IAM user (payflow-admin) for ALL daily work
   → Only login as root for billing settings and MFA changes
```

---

## Step 1.5: Create Access Keys (For CLI)

```
1. Still in IAM → Users → Click "payflow-admin"

2. Tab: "Security credentials"

3. Scroll to: "Access keys"

4. Click "Create access key"

5. Use case: "Command Line Interface (CLI)"
   Check: ✅ "I understand the above recommendation"
   Click "Next"

6. Tag (optional): "payflow-cli"
   Click "Create access key"

7. CRITICAL — SAVE THESE NOW:
   ┌─────────────────────────────────────────────────┐
   │ Access key ID: AKIAIOSFODNN7EXAMPLE              │
   │ Secret access key: wJalrXUtnFEMI/K7MDENG/bPx... │
   └─────────────────────────────────────────────────┘
   
   → Click "Download .csv file" (save somewhere safe!)
   → The secret key is SHOWN ONLY ONCE
   → If you lose it, you must create new keys

8. Click "Done"
```

---

## Step 1.6: Install and Configure AWS CLI

### Install AWS CLI

```
Windows:
1. Download: https://awscli.amazonaws.com/AWSCLIV2.msi
2. Run the installer (MSI)
3. Click Next → Next → Install → Finish
4. Open NEW Command Prompt/PowerShell

Verify:
> aws --version
aws-cli/2.x.x Python/3.x.x Windows/10 exe/AMD64
```

### Configure AWS CLI

```cmd
aws configure
```

```
AWS Access Key ID [None]: AKIAIOSFODNN7EXAMPLE
  → Paste your access key from Step 1.5

AWS Secret Access Key [None]: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
  → Paste your secret key from Step 1.5

Default region name [None]: ap-south-1
  → MUST match what we set in console (Mumbai)

Default output format [None]: json
  → JSON is easiest to read
```

### Verify CLI Works

```cmd
aws sts get-caller-identity
```

Expected output:
```json
{
    "UserId": "AIDAIOSFODNN7EXAMPLE",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/payflow-admin"
}
```

If you see this → AWS CLI is working! ✅

---

## Step 1.7: Set Up Budget Alert (DO THIS NOW!)

```
1. AWS Console (logged in as payflow-admin)
2. Search: "Billing" → Click "AWS Billing and Cost Management"
   (If you get "Access Denied" → need to enable IAM billing access:
    Login as root → Account → "IAM User and Role Access to Billing" → Activate)

3. Left sidebar: "Budgets"
4. Click "Create a budget"
5. Choose: "Use a template (simplified)"
6. Template: "Monthly cost budget"
7. Budget name: PayFlow Monthly Limit
8. Budgeted amount: 50.00 (USD)
9. Email recipients: your-email@gmail.com
10. Click "Create budget"

NOW SET FREE TIER ALERTS TOO:
11. Left sidebar: "Billing preferences"
12. Find: "Alert preferences"
13. Check: ✅ "Receive AWS Free Tier usage alerts"
14. Email: your-email@gmail.com
15. Save

WHAT HAPPENS:
├── If spending approaches $25 (50%) → email alert
├── If spending approaches $40 (80%) → email alert
├── If spending hits $50 (100%) → email alert
└── On Free Plan: account closes before bills (but alerts help you track)
```

---

## Step 1.8: Earn Additional $100 Credits

```
After account creation, you have $100 credits.
To earn $100 more (total $200), try these services:

1. Launch an EC2 instance (any type, even for 5 minutes) → +$20
2. Create an RDS database → +$20
3. Try Lambda (run a hello-world function) → +$20
4. Try Amazon Bedrock (AI) → +$20
5. Set up AWS Budgets (done in Step 1.7!) → +$20

Total: $100 + $100 = $200 credits available!

Note: We'll create EC2 and RDS in Phase 15 Parts 3-4, 
so those credits will be earned naturally as we deploy.
```

---

## Step 1.9: Verify Everything

Checklist:
```
✅ AWS account created (Free Plan)
✅ Can login to console at: https://console.aws.amazon.com
✅ Region set to ap-south-1 (Mumbai)
✅ Root account has MFA enabled (Authenticator app)
✅ IAM user "payflow-admin" created with AdminAccess
✅ Can login as IAM user via bookmark URL
✅ Access keys saved securely (CSV file)
✅ AWS CLI installed: aws --version shows 2.x.x
✅ AWS CLI configured: aws sts get-caller-identity works
✅ Budget alert set up ($50 monthly)
✅ Free tier alerts enabled
```

---

## Git Commit

```cmd
git add docs/phase15-part1-aws-account-and-free-tier.md
git commit -m "Phase 15 Part 1: AWS account setup guide (Free Plan, IAM, CLI, budgets)"
```

---

## What We Set Up

| Component | Purpose |
|-----------|---------|
| AWS account (Free Plan) | 6 months access + $200 credits |
| MFA on root | Protects against unauthorized access |
| IAM user (payflow-admin) | Daily work without risking root |
| Access keys | CLI and CI/CD authentication |
| AWS CLI | Deploy from command line |
| Budget alert ($50) | Warning before overspending |
| Free tier alerts | Warning if approaching service limits |

---

## Next Step

→ Continue to **Phase 15 Part 2: VPC, Subnets & Security Groups**

In Part 2, we create the network infrastructure:
- VPC (Virtual Private Cloud — our isolated network)
- Public subnet (for ALB — internet-facing)
- Private subnet (for EC2, RDS — NOT internet-facing)
- Security groups (firewall rules — who can talk to whom)
