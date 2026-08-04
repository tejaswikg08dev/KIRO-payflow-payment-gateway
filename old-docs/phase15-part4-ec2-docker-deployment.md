# Hands-On Guide — Phase 15 Part 4: EC2 Instances & Docker Deployment

## Goal

By the end of Part 4 you will have:
- 2 EC2 t3.micro instances launched (one in each AZ)
- Docker and Docker Compose installed on both
- SSH access configured (key pair)
- Docker images pushed to ECR (or built directly on EC2)
- All 11 services running in Docker on EC2
- Services connected to RDS and Redis
- Verified: API responds from EC2

## Prerequisites

- Phase 15 Part 3 completed (RDS, Redis, DynamoDB, SQS all running)
- All resource IDs saved
- SSH key pair available

---

## Step 4.1: Create Key Pair (For SSH Access)

**What is a key pair?** A pair of cryptographic keys:
- Private key (.pem file): stays on YOUR laptop (never share!)
- Public key: AWS puts this on EC2 (allows you to SSH in)

```cmd
aws ec2 create-key-pair ^
  --key-name payflow-key ^
  --query "KeyMaterial" ^
  --output text ^
  --region ap-south-1 > payflow-key.pem
```

**CRITICAL:**
```
├── This file (payflow-key.pem) is your ONLY way to SSH into EC2
├── If you lose it → you can NEVER access your instances
├── Save it somewhere safe (NOT in the git repo!)
├── On Windows: Keep in C:\Users\YourName\.ssh\payflow-key.pem
└── NEVER share this file with anyone
```

---

## Step 4.2: Launch EC2 Instance #1

```cmd
aws ec2 run-instances ^
  --image-id ami-0c768662cc797cd75 ^
  --instance-type t3.micro ^
  --key-name payflow-key ^
  --subnet-id %PRIVATE_SUBNET_1A% ^
  --security-group-ids %APP_SG% ^
  --associate-public-ip-address ^
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=payflow-ec2-1}]" ^
  --user-data file://ec2-userdata.sh ^
  --region ap-south-1
```

**Explanation:**
| Flag | Value | Why |
|------|-------|-----|
| `--image-id` | ami-0c768662cc797cd75 | Amazon Linux 2023 (latest, free) |
| `--instance-type` | t3.micro | 2 vCPU, 1 GB RAM (~$8.50/month) |
| `--key-name` | payflow-key | SSH key created in Step 4.1 |
| `--subnet-id` | Private subnet | Not directly internet-accessible |
| `--security-group-ids` | APP_SG | Only ALB can reach port 8080 |
| `--associate-public-ip-address` | — | Needed for SSH access (temporary) |
| `--user-data` | ec2-userdata.sh | Script that installs Docker on boot |

**⚠️ AMI ID changes per region!** Find latest Amazon Linux 2023:
```cmd
aws ec2 describe-images ^
  --owners amazon ^
  --filters "Name=name,Values=al2023-ami-2023*-x86_64" ^
  --query "Images | sort_by(@, &CreationDate) | [-1].ImageId" ^
  --output text ^
  --region ap-south-1
```

---

## Step 4.3: Create User Data Script (Installs Docker on Boot)

**Create file:** `ec2-userdata.sh` (this runs automatically when EC2 starts)

```bash
#!/bin/bash
# This script runs ONCE when the EC2 instance first boots

# Update packages
yum update -y

# Install Docker
yum install -y docker
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group (so we don't need sudo)
usermod -aG docker ec2-user

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Install Git (to clone our repo)
yum install -y git

# Install PostgreSQL client (to connect to RDS)
yum install -y postgresql15

echo "=== PayFlow EC2 Setup Complete ==="
```

---

## Step 4.4: Wait for Instance and Get IP

```cmd
:: Wait for instance to be running
aws ec2 wait instance-running --instance-ids i-0xxxxx --region ap-south-1

:: Get public IP
aws ec2 describe-instances ^
  --instance-ids i-0xxxxx ^
  --query "Reservations[0].Instances[0].PublicIpAddress" ^
  --output text ^
  --region ap-south-1
```

Save: `set EC2_IP=13.XX.XX.XX`

---

## Step 4.5: SSH into EC2

```cmd
ssh -i payflow-key.pem ec2-user@%EC2_IP%
```

**If "Permission denied":** On Windows, you may need to fix key permissions:
```cmd
icacls payflow-key.pem /inheritance:r
icacls payflow-key.pem /grant:r "%USERNAME%":R
```

**Once connected, verify Docker is installed:**
```bash
docker --version
# Docker version 24.x.x

docker-compose --version
# Docker Compose version v2.x.x
```

---

## Step 4.6: Initialize RDS Schemas (From EC2)

Now that we're on EC2 (same VPC as RDS), we can connect to PostgreSQL:

```bash
psql -h payflow-postgres.xxxx.ap-south-1.rds.amazonaws.com -U payflow_admin -d payflow
```

Enter password: `PayFlow2026SecureDB!`

**Run the schema creation:**
```sql
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS merchant;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS settlement;
\q
```

---

## Step 4.7: Deploy Services on EC2

### Option A: Clone repo and build on EC2
```bash
git clone https://github.com/YOUR_USERNAME/payflow-payment-gateway.git
cd payflow-payment-gateway

# Create .env file with production values
cat > .env << EOF
SPRING_DATASOURCE_URL=jdbc:postgresql://payflow-postgres.xxxx.rds.amazonaws.com:5432/payflow
SPRING_DATASOURCE_USERNAME=payflow_admin
SPRING_DATASOURCE_PASSWORD=PayFlow2026SecureDB!
SPRING_DATA_REDIS_HOST=payflow-redis.xxxx.cache.amazonaws.com
AWS_REGION=ap-south-1
EOF

# Start all services
docker-compose up -d
```

### Option B: Pull pre-built images from ECR
```bash
# Login to ECR
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin YOUR_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com

# Pull and run images
docker-compose -f docker-compose.prod.yml up -d
```

---

## Step 4.8: Verify Services Are Running

```bash
# Check all containers
docker ps

# Check API gateway health
curl http://localhost:8080/actuator/health

# Check identity service
curl http://localhost:8081/actuator/health

# Check payment service
curl http://localhost:8083/actuator/health
```

**Expected:** All return `{"status":"UP"}`

---

## Step 4.9: Test Payment Flow from EC2

```bash
# Register user
curl -X POST http://localhost:8081/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@demo.com","password":"Test123!","fullName":"Demo User","role":"MERCHANT"}'

# Should return JWT tokens!
```

If this works → your entire backend is deployed and functioning on AWS! 🎉

---

## Monthly Cost So Far

| Service | Monthly Cost |
|---------|:---:|
| VPC, subnets, IGW | $0 |
| EC2 t3.micro × 1 | ~$8.50 |
| RDS db.t3.micro | ~$15 |
| ElastiCache cache.t3.micro | ~$12 |
| DynamoDB, SQS, SNS | $0 |
| **Running total** | **~$35.50/month** |

Still well within $200 credits (lasts ~5.5 months at this rate).

---

## Git Commit

```cmd
git add docs/phase15-part4-ec2-docker-deployment.md
git add ec2-userdata.sh
git commit -m "Phase 15 Part 4: EC2 launch, Docker install, service deployment"
```

---

## Next Step

→ Continue to **Phase 15 Part 5: Application Load Balancer**
