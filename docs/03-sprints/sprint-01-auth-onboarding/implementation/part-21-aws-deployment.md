# Sprint 1, Part 21: AWS ECS Deployment

**Duration:** 3-4 hours  
**Prerequisites:** Part 20 completed, VPC and RDS created  
**Status:** 📘 CONCEPTUAL GUIDE (No source code required)

> **Note:** This part provides conceptual guidance for AWS ECS deployment. No source code files are created in this part - it documents the ECS configuration you would use when deploying to production.

---

## 1. What We're Building

In this part, you'll deploy services to **AWS ECS** (Elastic Container Service).

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ECS DEPLOYMENT ARCHITECTURE                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         ECS CLUSTER                                  │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                    ECS SERVICE                                │  │   │
│  │  │                                                               │  │   │
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐            │  │   │
│  │  │  │   Task     │  │   Task     │  │   Task     │            │  │   │
│  │  │  │ Container  │  │ Container  │  │ Container  │            │  │   │
│  │  │  └────────────┘  └────────────┘  └────────────┘            │  │   │
│  │  │                                                               │  │   │
│  │  │  Task Definition:                                             │  │   │
│  │  │  • Docker image                                               │  │   │
│  │  │  • CPU/Memory                                                 │  │   │
│  │  │  • Environment vars                                           │  │   │
│  │  │  • Port mappings                                              │  │   │
│  │  │                                                               │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  │  ┌────────────────────────────────────────────────────────────┐    │   │
│  │  │  Application Load Balancer                                  │    │   │
│  │  │  • Routes traffic to tasks                                  │    │   │
│  │  │  • Health checks                                            │    │   │
│  │  │  • SSL termination                                          │    │   │
│  │  └────────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 ECS Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ECS HIERARCHY                                             │
│                                                                              │
│  Cluster                                                                    │
│  └── Service                                                                │
│        └── Task (running container)                                         │
│              └── Container (from Task Definition)                           │
│                                                                              │
│  Task Definition                                                            │
│  ────────────────                                                          │
│  Blueprint for running containers:                                          │
│  • Docker image location                                                    │
│  • CPU/Memory requirements                                                  │
│  • Environment variables                                                    │
│  • Port mappings                                                            │
│  • IAM role                                                                 │
│  • Logging configuration                                                    │
│                                                                              │
│  Service                                                                    │
│  ───────                                                                    │
│  • Desired count of tasks                                                   │
│  • Load balancer configuration                                              │
│  • Auto-scaling rules                                                       │
│  • Deployment strategy                                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Create ECR Repository

```powershell
# Create repository for each service
aws ecr create-repository --repository-name payflow/identity-service
aws ecr create-repository --repository-name payflow/merchant-service
aws ecr create-repository --repository-name payflow/api-gateway
```

### Step 3.2: Push Docker Images

```powershell
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

# Build and tag
docker build -t payflow/identity-service ./identity-service
docker tag payflow/identity-service:latest ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/payflow/identity-service:latest

# Push
docker push ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/payflow/identity-service:latest
```

### Step 3.3: Create Task Definition

**File: `aws/task-definitions/identity-service.json`**

```json
{
  "family": "payflow-identity-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::ACCOUNT:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "identity-service",
      "image": "ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/payflow/identity-service:latest",
      "portMappings": [
        {
          "containerPort": 8081,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "aws"},
        {"name": "EUREKA_CLIENT_ENABLED", "value": "false"}
      ],
      "secrets": [
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:payflow/db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/payflow-identity-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

---

## 4. Key Takeaways

| Concept | Remember |
|---------|----------|
| **ECR** | Docker image registry |
| **Task Definition** | Container blueprint |
| **Service** | Manages running tasks |
| **Fargate** | Serverless containers |

---

## 5. Next Steps

**Continue to:** [part-22-e2e-testing.md](./part-22-e2e-testing.md)

---

**End of Sprint 1, Part 21**
