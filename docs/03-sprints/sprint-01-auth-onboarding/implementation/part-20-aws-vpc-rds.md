# Sprint 1, Part 20: AWS VPC & RDS Setup

**Duration:** 3-4 hours  
**Prerequisites:** Part 19 completed, AWS account with permissions  
**Status:** 📘 CONCEPTUAL GUIDE (No source code required)

> **Note:** This part provides conceptual guidance for AWS deployment. No source code files are created in this part - it documents the AWS infrastructure you would set up when deploying to production.

---

## 1. What We're Building

In this part, you'll set up **AWS infrastructure** for the PayFlow platform.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AWS ARCHITECTURE                                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         VPC (10.0.0.0/16)                            │   │
│  │                                                                      │   │
│  │  ┌──────────────────────┐    ┌──────────────────────┐              │   │
│  │  │   Public Subnet A    │    │   Public Subnet B    │              │   │
│  │  │   10.0.1.0/24        │    │   10.0.2.0/24        │              │   │
│  │  │                      │    │                      │              │   │
│  │  │  ┌──────────────┐   │    │  ┌──────────────┐   │              │   │
│  │  │  │   ALB        │   │    │  │   NAT GW     │   │              │   │
│  │  │  └──────────────┘   │    │  └──────────────┘   │              │   │
│  │  └──────────────────────┘    └──────────────────────┘              │   │
│  │                                                                      │   │
│  │  ┌──────────────────────┐    ┌──────────────────────┐              │   │
│  │  │  Private Subnet A    │    │  Private Subnet B    │              │   │
│  │  │  10.0.11.0/24        │    │  10.0.12.0/24        │              │   │
│  │  │                      │    │                      │              │   │
│  │  │  ┌──────────────┐   │    │  ┌──────────────┐   │              │   │
│  │  │  │  ECS Tasks   │   │    │  │  ECS Tasks   │   │              │   │
│  │  │  └──────────────┘   │    │  └──────────────┘   │              │   │
│  │  └──────────────────────┘    └──────────────────────┘              │   │
│  │                                                                      │   │
│  │  ┌──────────────────────┐    ┌──────────────────────┐              │   │
│  │  │  Database Subnet A   │    │  Database Subnet B   │              │   │
│  │  │  10.0.21.0/24        │    │  10.0.22.0/24        │              │   │
│  │  │                      │    │                      │              │   │
│  │  │  ┌──────────────┐   │    │  ┌──────────────┐   │              │   │
│  │  │  │  RDS Primary │   │    │  │  RDS Standby │   │              │   │
│  │  │  └──────────────┘   │    │  └──────────────┘   │              │   │
│  │  └──────────────────────┘    └──────────────────────┘              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 VPC Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VPC COMPONENTS EXPLAINED                                  │
│                                                                              │
│  Component         │ Purpose                                                │
│  ──────────────────┼────────────────────────────────────────────────────── │
│  VPC               │ Virtual private network, isolated from others          │
│  Subnets           │ Network segments within VPC                            │
│  Internet Gateway  │ VPC connection to internet                             │
│  NAT Gateway       │ Private subnet → internet (outbound only)             │
│  Route Tables      │ Traffic routing rules                                  │
│  Security Groups   │ Instance-level firewall                                │
│  NACLs             │ Subnet-level firewall                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    TRAFFIC FLOW                                    │    │
│  │                                                                     │    │
│  │  Internet                                                           │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  Internet Gateway                                                   │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  ALB (Public Subnet)                                                │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  ECS Tasks (Private Subnet)                                         │    │
│  │      │                                                              │    │
│  │      ▼                                                              │    │
│  │  RDS (Database Subnet)                                              │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Create VPC with AWS Console

**Or using AWS CLI:**

```powershell
# Create VPC
aws ec2 create-vpc \
  --cidr-block 10.0.0.0/16 \
  --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=payflow-vpc}]'

# Enable DNS hostnames
aws ec2 modify-vpc-attribute \
  --vpc-id vpc-xxx \
  --enable-dns-hostnames
```


### Step 3.2: Create RDS PostgreSQL

```powershell
# Create DB subnet group
aws rds create-db-subnet-group \
  --db-subnet-group-name payflow-db-subnet \
  --db-subnet-group-description "PayFlow database subnets" \
  --subnet-ids subnet-xxx subnet-yyy

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier payflow-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 15.4 \
  --master-username payflow \
  --master-user-password SECURE_PASSWORD \
  --allocated-storage 20 \
  --db-subnet-group-name payflow-db-subnet \
  --vpc-security-group-ids sg-xxx \
  --no-publicly-accessible
```

---

## 4. Key Takeaways

| Concept | Remember |
|---------|----------|
| **VPC** | Isolated network |
| **Public subnet** | Has internet access |
| **Private subnet** | No direct internet |
| **RDS** | Managed PostgreSQL |

---

## 5. Next Steps

**Continue to:** [part-21-aws-deployment.md](./part-21-aws-deployment.md)

---

**End of Sprint 1, Part 20**
