# Hands-On Guide — Phase 15 Part 5: Application Load Balancer (ALB)

## Goal

By the end of Part 5 you will have:
- ALB created in public subnets (internet-facing)
- Target group with EC2 instance registered
- Health checks configured (/actuator/health)
- ALB DNS name accessible from your browser
- Traffic flowing: Internet → ALB → EC2 (port 8080)

## Prerequisites

- Phase 15 Part 4 completed (EC2 running with services on port 8080)
- Public subnets exist (from Part 2)
- ALB security group exists (allows port 80/443 from internet)

---

## What Is an ALB?

```
WITHOUT ALB:
├── You'd access EC2 directly: http://13.XX.XX.XX:8080/v1/payments
├── Problems:
│   ├── IP address can change when EC2 restarts
│   ├── No HTTPS (customers see "Not Secure" warning)
│   ├── If EC2 crashes → entire system down (single point of failure)
│   └── Can't scale to multiple instances

WITH ALB:
├── You access: https://payflow-alb-XXXXXXX.ap-south-1.elb.amazonaws.com
├── Benefits:
│   ├── Stable DNS name (never changes)
│   ├── HTTPS with free AWS certificate
│   ├── Auto-distributes traffic across multiple EC2s
│   ├── Health checks: if EC2 is sick, ALB stops sending traffic
│   └── Single entry point for all services
```

---

## Step 5.1: Create Target Group

**What is a target group?** A list of EC2 instances that ALB can forward traffic to.

```cmd
aws elbv2 create-target-group ^
  --name payflow-api-tg ^
  --protocol HTTP ^
  --port 8080 ^
  --vpc-id %VPC_ID% ^
  --health-check-protocol HTTP ^
  --health-check-path /actuator/health ^
  --health-check-interval-seconds 30 ^
  --health-check-timeout-seconds 5 ^
  --healthy-threshold-count 2 ^
  --unhealthy-threshold-count 3 ^
  --target-type instance ^
  --region ap-south-1
```

**Health check explained:**
```
Every 30 seconds, ALB sends: GET /actuator/health to each EC2
├── If returns 200 twice in a row → "healthy" (keep sending traffic)
├── If returns non-200 three times → "unhealthy" (stop sending traffic)
├── Timeout: 5 seconds (if no response in 5s, counts as failure)
└── This ensures customers never hit a dead server
```

Save: `set TARGET_GROUP_ARN=arn:aws:elasticloadbalancing:ap-south-1:123456789012:targetgroup/payflow-api-tg/xxxx`

### Register EC2 Instance in Target Group:
```cmd
aws elbv2 register-targets ^
  --target-group-arn %TARGET_GROUP_ARN% ^
  --targets Id=i-0xxxxx ^
  --region ap-south-1
```

---

## Step 5.2: Create ALB

```cmd
aws elbv2 create-load-balancer ^
  --name payflow-alb ^
  --subnets %PUBLIC_SUBNET_1A% %PUBLIC_SUBNET_1B% ^
  --security-groups %ALB_SG% ^
  --scheme internet-facing ^
  --type application ^
  --region ap-south-1
```

**Key flags:**
| Flag | Value | Why |
|------|-------|-----|
| `--subnets` | Both public subnets | ALB needs 2 AZs (AWS requirement) |
| `--security-groups` | ALB_SG | Allows port 80/443 from internet |
| `--scheme` | internet-facing | Accessible from the internet |
| `--type` | application | Layer 7 (HTTP/HTTPS routing) |

Save: `set ALB_ARN=arn:aws:elasticloadbalancing:...`

**Get ALB DNS name:**
```cmd
aws elbv2 describe-load-balancers ^
  --names payflow-alb ^
  --query "LoadBalancers[0].DNSName" ^
  --output text ^
  --region ap-south-1
```

Result: `payflow-alb-1234567890.ap-south-1.elb.amazonaws.com`

**SAVE THIS! This is your public URL.**

---

## Step 5.3: Create Listener (Routes Traffic)

**What is a listener?** Tells ALB: "When traffic comes on port 80, forward to target group."

```cmd
aws elbv2 create-listener ^
  --load-balancer-arn %ALB_ARN% ^
  --protocol HTTP ^
  --port 80 ^
  --default-actions Type=forward,TargetGroupArn=%TARGET_GROUP_ARN% ^
  --region ap-south-1
```

This means: `Internet → ALB:80 → EC2:8080`

---

## Step 5.4: Test ALB

**Wait 2-3 minutes** for ALB to become "active" and health checks to pass.

### Check ALB status:
```cmd
aws elbv2 describe-load-balancers --names payflow-alb --query "LoadBalancers[0].State.Code" --output text --region ap-south-1
```
Wait until: `active`

### Check target health:
```cmd
aws elbv2 describe-target-health --target-group-arn %TARGET_GROUP_ARN% --region ap-south-1
```
Wait until: `"State": "healthy"`

### Test from your browser:
```
Open: http://payflow-alb-1234567890.ap-south-1.elb.amazonaws.com/actuator/health

Expected response:
{"status":"UP"}
```

### Test payment API:
```cmd
curl http://payflow-alb-1234567890.ap-south-1.elb.amazonaws.com/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"alb-test@demo.com\",\"password\":\"Test123!\",\"fullName\":\"ALB Test\",\"role\":\"MERCHANT\"}"
```

**If you get a response with JWT token → ALB is routing correctly! 🎉**

---

## Step 5.5: Summary of Traffic Flow

```
Your browser / Postman / curl
    │
    │ HTTP request to: payflow-alb-XXX.ap-south-1.elb.amazonaws.com
    ▼
┌─────────────────────────┐
│   ALB (Internet-facing)  │  ← In PUBLIC subnet
│   Port 80               │
│   Security: ALB_SG      │
└────────────┬────────────┘
             │ Forward to Target Group
             ▼
┌─────────────────────────┐
│   EC2 (payflow-ec2-1)   │  ← In PRIVATE subnet
│   Port 8080             │
│   Security: APP_SG      │
│   Docker: api-gateway   │
└────────────┬────────────┘
             │ api-gateway routes internally
             ▼
    identity-service (8081)
    payment-service (8083)
    routing-service (8084)
    etc.
```

---

## Monthly Cost Update

| Service | Monthly |
|---------|:---:|
| Previous (VPC + RDS + Redis + EC2) | ~$35.50 |
| ALB | ~$16 |
| **Total** | **~$51.50/month** |

$200 credits ÷ $51.50 = ~3.9 months. Plenty!

---

## Git Commit

```cmd
git add docs/phase15-part5-alb-and-routing.md
git commit -m "Phase 15 Part 5: ALB setup - target group, listener, health checks, traffic routing"
```

---

## Next Step

→ Continue to **Phase 15 Part 6: S3 + CloudFront (Frontend Hosting)**
