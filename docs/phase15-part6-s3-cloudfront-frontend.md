# Hands-On Guide — Phase 15 Part 6: S3 + CloudFront (Frontend Hosting)

## Goal

By the end of Part 6 you will have:
- S3 bucket hosting the React merchant dashboard (static files)
- CloudFront distribution serving the frontend globally (CDN)
- Frontend accessible via CloudFront URL (https://dxxxxx.cloudfront.net)
- Understanding of why S3 + CloudFront (not EC2) for frontends

## Prerequisites

- Phase 15 Part 5 completed (ALB routing backend traffic)
- React frontend built: `npm run build` produces `dist/` folder

---

## Why S3 + CloudFront (Not EC2) for Frontend?

```
React apps are STATIC files (HTML, CSS, JS, images).
They don't need a server to run — just file hosting.

EC2 for frontend:                 S3 + CloudFront for frontend:
├── $8.50/month (running 24/7)   ├── $0/month (always free tier!)
├── Must manage server            ├── Zero servers to manage
├── Single location (Mumbai)      ├── Served from 400+ edge locations globally
├── If EC2 crashes → site down    ├── Never goes down (99.99% SLA)
├── Must configure Nginx          ├── Just upload files
└── Slow for users far away       └── Fast everywhere (edge caching)

CLEAR WINNER: S3 + CloudFront for any frontend! ✅
```

---

## Step 6.1: Build React Frontend

**On your laptop:**
```cmd
cd payflow-payment-gateway/frontend-dashboard
npm run build
```

This creates `dist/` folder with:
```
dist/
├── index.html          (entry point)
├── assets/
│   ├── index-abc123.js  (bundled React app)
│   └── index-def456.css (bundled styles)
└── favicon.ico
```

---

## Step 6.2: Create S3 Bucket

```cmd
aws s3 mb s3://payflow-merchant-dashboard --region ap-south-1
```

**Bucket naming rules:**
- Must be globally unique (across ALL AWS accounts worldwide)
- Lowercase, no underscores
- If "payflow-merchant-dashboard" is taken, try: "payflow-dashboard-YOUR-NAME"

---

## Step 6.3: Configure Bucket for Static Website Hosting

```cmd
:: Enable static website hosting
aws s3 website s3://payflow-merchant-dashboard ^
  --index-document index.html ^
  --error-document index.html
```

**Why error-document = index.html?**
React is a Single Page App (SPA). When user navigates to `/dashboard/transactions`,
there's no actual file at that path. S3 would return 404.
By setting error-document to index.html, React Router handles all routes client-side.

---

## Step 6.4: Upload Frontend Files to S3

```cmd
:: Upload entire dist/ folder
aws s3 sync dist/ s3://payflow-merchant-dashboard/ --delete --region ap-south-1
```

**Flags:**
- `sync`: Upload only changed files (fast for re-deployments)
- `--delete`: Remove files from S3 that don't exist locally (clean sync)

**Verify:**
```cmd
aws s3 ls s3://payflow-merchant-dashboard/ --region ap-south-1
```

---

## Step 6.5: Create CloudFront Distribution

```cmd
aws cloudfront create-distribution ^
  --origin-domain-name payflow-merchant-dashboard.s3.ap-south-1.amazonaws.com ^
  --default-root-object index.html ^
  --region ap-south-1
```

**⏳ Takes 5-15 minutes to deploy globally.**

**Get distribution domain:**
```cmd
aws cloudfront list-distributions ^
  --query "DistributionList.Items[0].DomainName" ^
  --output text
```

Result: `dXXXXXXXXXX.cloudfront.net`

---

## Step 6.6: Configure Custom Error Responses (For React Router)

```cmd
:: This ensures React Router works for all paths
aws cloudfront update-distribution --id EXXXXXXXX ^
  --custom-error-responses "Quantity=1,Items=[{ErrorCode=403,ResponsePagePath=/index.html,ResponseCode=200,ErrorCachingMinTTL=10}]"
```

---

## Step 6.7: Test Frontend

**Open browser:**
```
https://dXXXXXXXXXX.cloudfront.net
```

You should see your merchant dashboard login page! 🎉

**Configure frontend to use ALB backend:**
In your React app's `.env.production`:
```
VITE_API_URL=http://payflow-alb-XXXXX.ap-south-1.elb.amazonaws.com
```

Rebuild and re-upload:
```cmd
npm run build
aws s3 sync dist/ s3://payflow-merchant-dashboard/ --delete
```

---

## Cost

| Service | Monthly |
|---------|:---:|
| S3 (under 5 GB) | $0 (always free) |
| CloudFront (under 1 TB) | $0 (always free) |
| **Frontend total** | **$0** |

---

## Git Commit

```cmd
git commit -m "Phase 15 Part 6: Frontend deployed to S3 + CloudFront"
```

---

## Next Step

→ Continue to **Phase 15 Part 7: End-to-End Verification**
