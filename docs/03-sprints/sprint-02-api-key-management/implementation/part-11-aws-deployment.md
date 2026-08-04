# Sprint 2, Part 11: AWS Deployment

**Duration:** 1 hour  
**Prerequisites:** Part 10 completed, AWS account configured  
**Goal:** Deploy Sprint 2 changes to AWS

---

## 1. Learning Objectives

By the end of this part, you will:
- Understand which AWS resources need updates for Sprint 2
- Deploy updated services to ECS
- Verify API key authentication works in AWS environment

---

## 2. Sprint 2 Deployment Scope

### 2.1 What Needs Deployment

| Component | Change | Deployment Action |
|-----------|--------|-------------------|
| api-gateway | Added ApiKeyAuthFilter | Rebuild & redeploy ECS task |
| merchant-service | Added endpoints | Rebuild & redeploy ECS task |
| frontend-dashboard | Added ApiKeysPage | Rebuild & redeploy to S3/CloudFront |

### 2.2 No Infrastructure Changes

Sprint 2 doesn't require new AWS resources:
- ✅ ElastiCache (Redis) already provisioned in Sprint 1
- ✅ RDS (PostgreSQL) already provisioned
- ✅ ECS cluster already running
- ✅ ALB already configured

---

## 3. Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AWS DEPLOYMENT (SPRINT 2)                                 │
│                                                                              │
│  CloudFront                                                                  │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────┐                                                        │
│  │ S3 (Frontend)   │  ← npm run build → Upload to S3                       │
│  │ + ApiKeysPage   │                                                        │
│  └─────────────────┘                                                        │
│                                                                              │
│  Route 53 → ALB                                                             │
│                │                                                             │
│                ├─── /v1/auth/*     → ECS: identity-service                  │
│                │                                                             │
│                ├─── /v1/merchants/* → ECS: merchant-service (UPDATED)       │
│                │                          + list/revoke endpoints           │
│                │                          + webhook endpoints                │
│                │                                                             │
│                └─── Gateway filters:                                        │
│                     ├── CorrelationIdFilter                                 │
│                     ├── RateLimitFilter                                     │
│                     └── ApiKeyAuthFilter (NEW)                              │
│                                │                                             │
│                                ▼                                             │
│                     ┌─────────────────┐                                     │
│                     │ ElastiCache     │  ← Cache API key validations       │
│                     │ (Redis)         │                                     │
│                     └─────────────────┘                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Deployment Steps

### 4.1 Build New Docker Images

```powershell
# Build all services
docker compose build api-gateway merchant-service

# Tag for ECR
docker tag payflow-api-gateway:latest <account>.dkr.ecr.<region>.amazonaws.com/payflow-api-gateway:sprint2
docker tag payflow-merchant-service:latest <account>.dkr.ecr.<region>.amazonaws.com/payflow-merchant-service:sprint2
```

### 4.2 Push to ECR

```powershell
# Login to ECR
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com

# Push images
docker push <account>.dkr.ecr.<region>.amazonaws.com/payflow-api-gateway:sprint2
docker push <account>.dkr.ecr.<region>.amazonaws.com/payflow-merchant-service:sprint2
```

### 4.3 Update ECS Task Definitions

Update the image tag in task definitions to `:sprint2`

```powershell
# Update via AWS CLI or Console
aws ecs update-service --cluster payflow-cluster --service api-gateway --force-new-deployment
aws ecs update-service --cluster payflow-cluster --service merchant-service --force-new-deployment
```

### 4.4 Deploy Frontend

```powershell
cd frontend-dashboard

# Build for production
npm run build

# Sync to S3
aws s3 sync dist/ s3://payflow-frontend-bucket --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation --distribution-id <dist-id> --paths "/*"
```

---

## 5. Environment Configuration

### 5.1 API Gateway ECS Task

Ensure these environment variables are set:

```json
{
  "name": "SPRING_DATA_REDIS_HOST",
  "value": "payflow-redis.xxxxx.cache.amazonaws.com"
},
{
  "name": "MERCHANT_SERVICE_URL",
  "value": "http://merchant-service.payflow.local:8082"
}
```

### 5.2 Update ApiKeyAuthFilter for AWS

For AWS deployment, update the WebClient base URL to use service discovery:

```java
// In ApiKeyAuthFilter constructor
this.webClient = webClientBuilder
    .baseUrl(System.getenv().getOrDefault("MERCHANT_SERVICE_URL", "http://localhost:8082"))
    .build();
```

---

## 6. Verification

### 6.1 Test API Key Generation

```powershell
# Via ALB endpoint
curl -X POST "https://api.payflow.com/v1/merchants/merch_xxx/api-keys?keyType=TEST" `
  -H "Authorization: Bearer <jwt_token>"
```

### 6.2 Test API Key Authentication

```powershell
# Using generated key
curl https://api.payflow.com/v1/merchants/merch_xxx `
  -H "X-Api-Key: sk_test_xxxxxxxxxxxxx"
```

### 6.3 Test Frontend

1. Navigate to `https://dashboard.payflow.com`
2. Login
3. Click "Manage API Keys"
4. Generate and revoke keys

---

## 7. Rollback Plan

If issues occur:

```powershell
# Rollback ECS to previous task definition
aws ecs update-service --cluster payflow-cluster --service api-gateway --task-definition payflow-api-gateway:sprint1

# Rollback frontend (restore previous S3 version)
aws s3 sync s3://payflow-frontend-bucket-backup s3://payflow-frontend-bucket
```

---

## 8. Monitoring

### 8.1 CloudWatch Metrics

Monitor these metrics after deployment:
- ECS CPU/Memory utilization
- ALB request count and latency
- ElastiCache hit rate
- 4xx/5xx error rates

### 8.2 CloudWatch Logs

Check logs for errors:
- `/ecs/api-gateway` - Look for ApiKeyAuthFilter logs
- `/ecs/merchant-service` - Look for key generation/revocation logs

---

## 9. Key Takeaways

| Aspect | Status |
|--------|--------|
| New infrastructure | Not required |
| Service updates | api-gateway, merchant-service |
| Frontend | S3 + CloudFront invalidation |
| Redis | Already available (ElastiCache) |

---

## 10. Next Steps

**Continue to:** [part-12-e2e-testing.md](./part-12-e2e-testing.md)

In the next part, you'll perform end-to-end testing of the deployed system.

---

**End of Sprint 2, Part 11**
