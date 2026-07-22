# Hands-On Guide — Phase 15 Part 2: VPC, Subnets & Security Groups

## Goal

By the end of Part 2 you will have:
- VPC created (your own isolated private network in AWS)
- 2 public subnets (for ALB — internet-facing)
- 2 private subnets (for EC2 and RDS — NOT accessible from internet)
- Internet Gateway (lets public subnet reach internet)
- Route tables configured (traffic routing rules)
- 3 security groups created (ALB, App, Database firewalls)
- Understanding of WHY we need each component

## Prerequisites

- Phase 15 Part 1 completed (AWS account, IAM user, CLI configured)
- Logged in as IAM user (payflow-admin)
- Region set to ap-south-1 (Mumbai)
- AWS CLI working: `aws sts get-caller-identity` returns your account

---

## What Is a VPC? (Explain Like I'm 5)

```
REAL WORLD ANALOGY:

VPC = Your own private building (office complex)
├── The building has a fence (no one gets in without permission)
├── Inside: multiple floors (subnets)
│   ├── Ground floor (public subnet): Reception desk, visitors allowed
│   └── Upper floors (private subnet): Server room, employees only
├── Security guards (security groups): Check ID before letting anyone in
├── Main gate (Internet Gateway): Connects building to the road (internet)
└── Intercom (Route tables): Tells people how to reach each floor

WITHOUT VPC:
├── Your EC2 instances would be on a shared network with other AWS customers
├── Anyone could try to access your database
├── No control over network traffic
└── NOT acceptable for payment systems!

WITH VPC:
├── Your services are in YOUR isolated network
├── You control: who can access what
├── Database is in private subnet (NOT reachable from internet)
├── Only ALB in public subnet (accepts HTTPS from internet)
├── ALB forwards to EC2 in private subnet
└── This is how ALL payment companies set up their infrastructure
```

---

## Network Architecture We're Building

```
┌────────────────────────── VPC: 10.0.0.0/16 ──────────────────────────────┐
│                                                                            │
│  ┌─── Availability Zone: ap-south-1a ───┐  ┌─── AZ: ap-south-1b ──────┐ │
│  │                                       │  │                           │ │
│  │  PUBLIC SUBNET: 10.0.1.0/24          │  │  PUBLIC SUBNET: 10.0.2.0/24│ │
│  │  ┌─────────────────────────┐         │  │  ┌─────────────────────┐  │ │
│  │  │ ALB (Load Balancer)     │         │  │  │ ALB (spans both AZs)│  │ │
│  │  │ Internet Gateway access │         │  │  │                     │  │ │
│  │  └─────────────────────────┘         │  │  └─────────────────────┘  │ │
│  │                                       │  │                           │ │
│  │  PRIVATE SUBNET: 10.0.3.0/24        │  │  PRIVATE SUBNET: 10.0.4.0/24│ │
│  │  ┌─────────────────────────┐         │  │  ┌─────────────────────┐  │ │
│  │  │ EC2 Instance #1         │         │  │  │ EC2 Instance #2     │  │ │
│  │  │ (Docker: all services)  │         │  │  │ (Docker: backup)    │  │ │
│  │  │                         │         │  │  │                     │  │ │
│  │  │ RDS PostgreSQL (primary)│         │  │  │                     │  │ │
│  │  │ ElastiCache Redis       │         │  │  │                     │  │ │
│  │  └─────────────────────────┘         │  │  └─────────────────────┘  │ │
│  └───────────────────────────────────────┘  └───────────────────────────┘ │
│                                                                            │
│  Internet Gateway ←→ Public subnets ←→ ALB ←→ Private subnets (EC2, RDS)  │
└────────────────────────────────────────────────────────────────────────────┘

WHO CAN TALK TO WHOM:
├── Internet → ALB (port 80, 443): YES (public subnet)
├── Internet → EC2 directly: NO (private subnet, no direct access)
├── Internet → RDS directly: NO (private subnet, blocked)
├── ALB → EC2 (port 8080): YES (security group allows it)
├── EC2 → RDS (port 5432): YES (security group allows it)
├── EC2 → Redis (port 6379): YES (security group allows it)
└── EC2 → Internet (for updates): YES (via NAT or public subnet workaround)
```

---

## Why TWO Availability Zones?

```
An Availability Zone (AZ) = one physical data center.
We use TWO AZs because:

├── AZ-1 has a power outage → AZ-2 keeps running (high availability)
├── ALB requires minimum 2 AZs (AWS rule)
├── RDS can have standby in second AZ (automatic failover)
└── For our demo: we'll put main resources in AZ-1, ALB spans both

AZs in ap-south-1 (Mumbai): ap-south-1a, ap-south-1b, ap-south-1c
We use: ap-south-1a (main) and ap-south-1b (secondary)
```

---

## Step 2.1: Create VPC

```cmd
aws ec2 create-vpc ^
  --cidr-block 10.0.0.0/16 ^
  --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=payflow-vpc}]" ^
  --region ap-south-1
```

**What is CIDR 10.0.0.0/16?**
```
CIDR (Classless Inter-Domain Routing) defines IP address range:
10.0.0.0/16 means:
├── 10.0.X.X — first two octets are fixed (10.0)
├── Last two octets are ours to use (0.0 to 255.255)
├── Total IPs available: 65,536 (more than enough!)
└── We'll carve this into 4 subnets of 256 IPs each (/24)
```

**Expected output (save the VpcId!):**
```json
{
    "Vpc": {
        "VpcId": "vpc-0abc123def456789",
        "CidrBlock": "10.0.0.0/16",
        "State": "available"
    }
}
```

**Save VPC ID for later commands:**
```cmd
set VPC_ID=vpc-0abc123def456789
```

**Enable DNS hostnames (needed for RDS and service discovery):**
```cmd
aws ec2 modify-vpc-attribute --vpc-id %VPC_ID% --enable-dns-hostnames "{\"Value\":true}" --region ap-south-1
aws ec2 modify-vpc-attribute --vpc-id %VPC_ID% --enable-dns-support "{\"Value\":true}" --region ap-south-1
```

---

## Step 2.2: Create Subnets (4 Total)

### Public Subnet in AZ-1a (for ALB):
```cmd
aws ec2 create-subnet ^
  --vpc-id %VPC_ID% ^
  --cidr-block 10.0.1.0/24 ^
  --availability-zone ap-south-1a ^
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=payflow-public-1a}]" ^
  --region ap-south-1
```
Save: `set PUBLIC_SUBNET_1A=subnet-0aaaa...`

### Public Subnet in AZ-1b (ALB needs 2 AZs):
```cmd
aws ec2 create-subnet ^
  --vpc-id %VPC_ID% ^
  --cidr-block 10.0.2.0/24 ^
  --availability-zone ap-south-1b ^
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=payflow-public-1b}]" ^
  --region ap-south-1
```
Save: `set PUBLIC_SUBNET_1B=subnet-0bbbb...`

### Private Subnet in AZ-1a (for EC2, RDS):
```cmd
aws ec2 create-subnet ^
  --vpc-id %VPC_ID% ^
  --cidr-block 10.0.3.0/24 ^
  --availability-zone ap-south-1a ^
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=payflow-private-1a}]" ^
  --region ap-south-1
```
Save: `set PRIVATE_SUBNET_1A=subnet-0cccc...`

### Private Subnet in AZ-1b (RDS standby, second EC2):
```cmd
aws ec2 create-subnet ^
  --vpc-id %VPC_ID% ^
  --cidr-block 10.0.4.0/24 ^
  --availability-zone ap-south-1b ^
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=payflow-private-1b}]" ^
  --region ap-south-1
```
Save: `set PRIVATE_SUBNET_1B=subnet-0dddd...`

**Enable auto-assign public IP for public subnets:**
```cmd
aws ec2 modify-subnet-attribute --subnet-id %PUBLIC_SUBNET_1A% --map-public-ip-on-launch --region ap-south-1
aws ec2 modify-subnet-attribute --subnet-id %PUBLIC_SUBNET_1B% --map-public-ip-on-launch --region ap-south-1
```

---

## Step 2.3: Create Internet Gateway

**What is it?** The "door" that connects your VPC to the internet.
Without it, nothing in your VPC can reach the internet (or be reached from internet).

```cmd
aws ec2 create-internet-gateway ^
  --tag-specifications "ResourceType=internet-gateway,Tags=[{Key=Name,Value=payflow-igw}]" ^
  --region ap-south-1
```
Save: `set IGW_ID=igw-0eeee...`

**Attach it to your VPC:**
```cmd
aws ec2 attach-internet-gateway --internet-gateway-id %IGW_ID% --vpc-id %VPC_ID% --region ap-south-1
```

---

## Step 2.4: Create Route Tables

**What are route tables?** Rules that tell traffic where to go.
- Public subnets: "If destination is internet (0.0.0.0/0) → use Internet Gateway"
- Private subnets: "No route to internet" (isolated)

### Public Route Table:
```cmd
aws ec2 create-route-table ^
  --vpc-id %VPC_ID% ^
  --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=payflow-public-rt}]" ^
  --region ap-south-1
```
Save: `set PUBLIC_RT=rtb-0ffff...`

**Add route: "0.0.0.0/0 → Internet Gateway" (all internet traffic goes through IGW):**
```cmd
aws ec2 create-route --route-table-id %PUBLIC_RT% --destination-cidr-block 0.0.0.0/0 --gateway-id %IGW_ID% --region ap-south-1
```

**Associate public subnets with this route table:**
```cmd
aws ec2 associate-route-table --route-table-id %PUBLIC_RT% --subnet-id %PUBLIC_SUBNET_1A% --region ap-south-1
aws ec2 associate-route-table --route-table-id %PUBLIC_RT% --subnet-id %PUBLIC_SUBNET_1B% --region ap-south-1
```

(Private subnets use the VPC's main/default route table — which has NO internet route.)

---

## Step 2.5: Create Security Groups

**What are security groups?** Firewalls that control who can access what.
Think of them as "rules at the door" — who is allowed in, on which port.

### Security Group 1: ALB (internet-facing)
```cmd
aws ec2 create-security-group ^
  --group-name payflow-alb-sg ^
  --description "ALB - accepts HTTP/HTTPS from internet" ^
  --vpc-id %VPC_ID% ^
  --region ap-south-1
```
Save: `set ALB_SG=sg-0gggg...`

**Allow HTTP (80) and HTTPS (443) from anywhere:**
```cmd
aws ec2 authorize-security-group-ingress --group-id %ALB_SG% --protocol tcp --port 80 --cidr 0.0.0.0/0 --region ap-south-1
aws ec2 authorize-security-group-ingress --group-id %ALB_SG% --protocol tcp --port 443 --cidr 0.0.0.0/0 --region ap-south-1
```

### Security Group 2: Application (EC2 — only from ALB)
```cmd
aws ec2 create-security-group ^
  --group-name payflow-app-sg ^
  --description "App servers - only ALB can reach them" ^
  --vpc-id %VPC_ID% ^
  --region ap-south-1
```
Save: `set APP_SG=sg-0hhhh...`

**Allow traffic ONLY from ALB (not from internet!):**
```cmd
aws ec2 authorize-security-group-ingress --group-id %APP_SG% --protocol tcp --port 8080 --source-group %ALB_SG% --region ap-south-1
aws ec2 authorize-security-group-ingress --group-id %APP_SG% --protocol tcp --port 22 --cidr YOUR_HOME_IP/32 --region ap-south-1
```
(Port 22 = SSH access from YOUR IP only, for deployment)

### Security Group 3: Database (only from App)
```cmd
aws ec2 create-security-group ^
  --group-name payflow-db-sg ^
  --description "Database - only app servers can reach" ^
  --vpc-id %VPC_ID% ^
  --region ap-south-1
```
Save: `set DB_SG=sg-0iiii...`

**Allow PostgreSQL (5432) and Redis (6379) ONLY from app servers:**
```cmd
aws ec2 authorize-security-group-ingress --group-id %DB_SG% --protocol tcp --port 5432 --source-group %APP_SG% --region ap-south-1
aws ec2 authorize-security-group-ingress --group-id %DB_SG% --protocol tcp --port 6379 --source-group %APP_SG% --region ap-south-1
```

---

## Step 2.6: Verify Everything

```cmd
:: Check VPC exists
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=payflow-vpc" --query "Vpcs[0].{VpcId:VpcId,State:State,Cidr:CidrBlock}" --output table --region ap-south-1

:: Check subnets (should show 4)
aws ec2 describe-subnets --filters "Name=vpc-id,Values=%VPC_ID%" --query "Subnets[*].{Name:Tags[0].Value,Cidr:CidrBlock,AZ:AvailabilityZone}" --output table --region ap-south-1

:: Check security groups (should show 3 + default)
aws ec2 describe-security-groups --filters "Name=vpc-id,Values=%VPC_ID%" --query "SecurityGroups[*].{Name:GroupName,Id:GroupId}" --output table --region ap-south-1

:: Check internet gateway attached
aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=%VPC_ID%" --query "InternetGateways[0].InternetGatewayId" --output text --region ap-south-1
```

**Expected:** All resources exist and are in correct state.

---

## Step 2.7: Save All IDs (You'll Need These Later!)

Create a file to store all resource IDs:

```cmd
echo VPC_ID=%VPC_ID% > aws-resources.txt
echo PUBLIC_SUBNET_1A=%PUBLIC_SUBNET_1A% >> aws-resources.txt
echo PUBLIC_SUBNET_1B=%PUBLIC_SUBNET_1B% >> aws-resources.txt
echo PRIVATE_SUBNET_1A=%PRIVATE_SUBNET_1A% >> aws-resources.txt
echo PRIVATE_SUBNET_1B=%PRIVATE_SUBNET_1B% >> aws-resources.txt
echo IGW_ID=%IGW_ID% >> aws-resources.txt
echo ALB_SG=%ALB_SG% >> aws-resources.txt
echo APP_SG=%APP_SG% >> aws-resources.txt
echo DB_SG=%DB_SG% >> aws-resources.txt
```

⚠️ **DO NOT commit this file to Git** (contains AWS resource IDs).
Add to .gitignore: `aws-resources.txt`

---

## Common Mistakes to Avoid

| Mistake | Consequence | How to Avoid |
|---------|------------|--------------|
| Creating resources in wrong region | Can't find them, can't connect | ALWAYS check region = ap-south-1 |
| Putting EC2 in public subnet | Database exposed to internet | Use private subnets for app/DB |
| Opening port 5432 to 0.0.0.0/0 | Anyone can access your database! | Only allow from APP security group |
| Forgetting to attach IGW | Public subnet can't reach internet | Run attach-internet-gateway command |
| Creating NAT Gateway | $32/month waste | Put EC2 in public subnet instead (for demo) |

---

## Git Commit

```cmd
git add docs/phase15-part2-vpc-subnets-security-groups.md
git add aws-resources.txt
git commit -m "Phase 15 Part 2: VPC networking - subnets, IGW, route tables, security groups"
```

---

## What We Created

| Resource | Count | Purpose |
|----------|:---:|--------|
| VPC | 1 | Isolated private network (10.0.0.0/16) |
| Public subnets | 2 | ALB (internet-facing) in 2 AZs |
| Private subnets | 2 | EC2 + RDS (not internet-accessible) |
| Internet Gateway | 1 | Connects VPC to internet |
| Route table (public) | 1 | Routes internet traffic via IGW |
| Security group (ALB) | 1 | Allows HTTP/HTTPS from internet |
| Security group (App) | 1 | Allows traffic only from ALB |
| Security group (DB) | 1 | Allows traffic only from App servers |

---

## Interview Notes

**Q: "How did you set up networking on AWS?"**
> "I created a VPC with public and private subnets across two availability zones. The ALB sits in public subnets (internet-facing). EC2 and RDS are in private subnets — not directly accessible from the internet. Security groups act as firewalls: ALB accepts HTTPS from anywhere, app servers accept only from ALB, and databases accept only from app servers. This layered approach means even if someone compromises the ALB, they can't directly reach the database."

**Q: "Why two availability zones?"**
> "ALB requires minimum two AZs. Also, if one data center has an outage, the other keeps running. For RDS, we can enable Multi-AZ for automatic failover (though we skip it for demo to save money)."

---

## Next Step

→ Continue to **Phase 15 Part 3: RDS, ElastiCache & DynamoDB**
