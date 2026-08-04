# Phase 14 Part 2 — GitHub Actions Backend Pipeline

## Goal
- Create CI workflow for Java services (build, test, Docker push)
- Run unit and integration tests with service containers
- Push Docker images to GitHub Container Registry (GHCR)

## Key Concept

```
┌────────────────────────────────────────────────────────┐
│  Backend CI Pipeline (on push to main/PR)              │
│                                                        │
│  ┌─────────┐  ┌──────┐  ┌───────┐  ┌──────────────┐  │
│  │ Checkout │→│ Build │→│ Test  │→│ Docker Build  │  │
│  │   Code  │  │ Maven│  │JUnit  │  │ & Push GHCR  │  │
│  └─────────┘  └──────┘  └───────┘  └──────────────┘  │
│                             │                          │
│                     ┌───────┴───────┐                  │
│                     │ Postgres (svc)│                  │
│                     │ Redis (svc)   │                  │
│                     └───────────────┘                  │
└────────────────────────────────────────────────────────┘
```

## Prerequisites
- GitHub repository created
- Repository secrets configured: `GHCR_TOKEN` (or use `GITHUB_TOKEN`)

## Step-by-Step

### 1. Create Workflow File (`.github/workflows/ci-backend.yml`)

```yaml
name: Backend CI

on:
  push:
    branches: [main, develop]
    paths:
      - 'identity-service/**'
      - 'payment-service/**'
      - 'api-gateway/**'
      - 'common-lib/**'
      - 'pom.xml'
  pull_request:
    branches: [main]
    paths:
      - 'identity-service/**'
      - 'payment-service/**'
      - 'api-gateway/**'
      - 'common-lib/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: payflow_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build common-lib
        run: mvn -pl common-lib install -DskipTests

      - name: Build & Test all services
        run: mvn clean verify -pl identity-service,payment-service,api-gateway
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/payflow_test
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test
          SPRING_REDIS_HOST: localhost

      - name: Upload coverage report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-reports
          path: '**/target/site/jacoco/'

  docker-build:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    strategy:
      matrix:
        service: [identity-service, payment-service, api-gateway]
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.service }}
          push: true
          tags: |
            ghcr.io/${{ github.repository }}/${{ matrix.service }}:latest
            ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### 2. Add Branch Protection (via GitHub settings)

```
Settings → Branches → Add rule for "main":
- Require status checks: "build-and-test"
- Require PR review before merge
- Dismiss stale reviews
```

## Verification

```bash
# Push to trigger workflow
git push origin main

# Check Actions tab in GitHub
# Expected: Green checkmark on all jobs
# build-and-test: ~3-5 minutes
# docker-build: ~2-3 minutes per service (parallel)

# Verify images pushed
docker pull ghcr.io/<your-org>/payflow-payment-gateway/payment-service:latest
```

## Git Commit

```bash
git add .github/workflows/ci-backend.yml
git commit -m "ci: add backend CI pipeline with test and Docker build"
```

## Next Step
→ **Phase 14 Part 3** — Frontend CI pipeline
