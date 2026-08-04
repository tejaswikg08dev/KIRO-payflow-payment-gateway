# Sprint 1, Part 19: CI/CD Backend

**Duration:** 1-2 hours  
**Prerequisites:** Part 18 completed, GitHub repository

---

## 1. What We're Building

In this part, you'll understand the **GitHub Actions CI/CD pipeline** for backend services.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CI/CD PIPELINE OVERVIEW                                  │
│                                                                              │
│  Developer                                                                  │
│      │                                                                       │
│      │ git push                                                              │
│      ▼                                                                       │
│  GitHub                                                                     │
│      │                                                                       │
│      │ triggers                                                              │
│      ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    GitHub Actions                                    │   │
│  │                                                                      │   │
│  │  Job 1: build-and-test                                              │   │
│  │  ├── Checkout code                                                  │   │
│  │  ├── Setup JDK 17                                                   │   │
│  │  ├── Build common-lib                                               │   │
│  │  ├── Compile all services                                           │   │
│  │  ├── Run unit tests                                                 │   │
│  │  ├── Run integration tests                                          │   │
│  │  └── Upload test results                                            │   │
│  │                                                                      │   │
│  │  Job 2: docker-build (main branch only)                             │   │
│  │  ├── Build Docker images for each service                           │   │
│  │  └── Push to GitHub Container Registry                              │   │
│  │                                                                      │   │
│  │  Job 3: security-scan (main branch only)                            │   │
│  │  └── Trivy vulnerability scanner                                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 GitHub Actions Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GITHUB ACTIONS HIERARCHY                                  │
│                                                                              │
│  Workflow (.github/workflows/ci-backend.yml)                               │
│  │                                                                          │
│  ├── Triggers (on:)                                                        │
│  │     ├── push to main, develop                                           │
│  │     └── pull_request to main                                            │
│  │                                                                          │
│  └── Jobs (parallel by default)                                            │
│        ├── build-and-test                                                  │
│        │     └── Steps (sequential)                                        │
│        │           ├── actions/checkout@v4                                 │
│        │           ├── actions/setup-java@v4                               │
│        │           ├── mvn compile                                         │
│        │           └── mvn test                                            │
│        │                                                                    │
│        ├── docker-build (needs: build-and-test)                            │
│        │     └── Runs only if build-and-test succeeds                      │
│        │                                                                    │
│        └── security-scan (needs: docker-build)                             │
│              └── Runs only if docker-build succeeds                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Path Filters

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PATH FILTERING                                            │
│                                                                              │
│  The CI only runs when relevant files change:                              │
│                                                                              │
│  paths:                                                                     │
│    - 'identity-service/**'    ← Java service changes                       │
│    - 'payment-service/**'                                                  │
│    - 'api-gateway/**'                                                      │
│    - 'bank-simulator/**'                                                   │
│    - 'common-lib/**'          ← Shared library changes                     │
│    - 'pom.xml'                ← Root POM changes                           │
│                                                                              │
│  Benefits:                                                                  │
│  • Frontend changes don't trigger backend CI                               │
│  • Docs changes don't trigger any CI                                       │
│  • Saves GitHub Actions minutes                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

- GitHub repository created
- Code pushed to repository
- GitHub Actions enabled (enabled by default)

---

## 4. The CI/CD Workflow

### Step 4.1: Backend CI Workflow

**File: `.github/workflows/ci-backend.yml`**

```yaml
name: Backend CI

on:
  push:
    branches: [main, develop]
    paths:
      - 'identity-service/**'
      - 'payment-service/**'
      - 'api-gateway/**'
      - 'bank-simulator/**'
      - 'common-lib/**'
      - 'pom.xml'
  pull_request:
    branches: [main]
    paths:
      - 'identity-service/**'
      - 'payment-service/**'
      - 'api-gateway/**'
      - 'bank-simulator/**'
      - 'common-lib/**'

env:
  JAVA_VERSION: '17'
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ghcr.io/${{ github.repository }}

jobs:
  # ─── Build & Test ─────────────────────────────────────
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
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Build common-lib
        run: mvn -pl common-lib install -DskipTests -B

      - name: Compile all services
        run: mvn compile -pl identity-service,payment-service,api-gateway,bank-simulator -am -B

      - name: Run unit tests
        run: mvn test -pl identity-service,payment-service,api-gateway -B
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/payflow_test
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test
          SPRING_REDIS_HOST: localhost
          SPRING_REDIS_PORT: 6379

      - name: Run integration tests
        run: mvn verify -pl payment-service,identity-service -B -Dgroups=integration
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/payflow_test
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test
          SPRING_REDIS_HOST: localhost

      - name: Generate JaCoCo coverage report
        run: mvn jacoco:report -pl identity-service,payment-service -B

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: '**/target/surefire-reports/'

      - name: Upload coverage reports
        uses: actions/upload-artifact@v4
        with:
          name: coverage-reports
          path: '**/target/site/jacoco/'

  # ─── Docker Build & Push ──────────────────────────────
  docker-build:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'

    strategy:
      matrix:
        service:
          - identity-service
          - payment-service
          - api-gateway
          - bank-simulator

    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.IMAGE_PREFIX }}/${{ matrix.service }}
          tags: |
            type=sha,prefix=
            type=raw,value=latest

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./${{ matrix.service }}/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # ─── Security Scan ────────────────────────────────────
  security-scan:
    needs: docker-build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'

    strategy:
      matrix:
        service: [identity-service, payment-service, api-gateway]

    steps:
      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:latest
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'

      - name: Upload scan results
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
```

**Workflow Explained:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WORKFLOW BREAKDOWN                                        │
│                                                                              │
│  TRIGGERS (on:)                                                             │
│  ──────────────                                                             │
│  push to main/develop     → Full CI + Docker build + Security scan         │
│  pull_request to main     → Only build and test (no Docker push)           │
│                                                                              │
│  SERVICE CONTAINERS                                                         │
│  ──────────────────                                                         │
│  services:                                                                  │
│    postgres:              ← Spins up PostgreSQL for tests                  │
│    redis:                 ← Spins up Redis for tests                       │
│                                                                              │
│  These run alongside the job and are accessible via localhost               │
│                                                                              │
│  BUILD STEPS                                                                │
│  ───────────                                                                │
│  1. Checkout code         → actions/checkout@v4                            │
│  2. Setup JDK             → actions/setup-java@v4 with cache               │
│  3. Build common-lib      → Install shared library to local Maven repo     │
│  4. Compile services      → mvn compile for all services                   │
│  5. Run unit tests        → mvn test with service containers               │
│  6. Run integration tests → mvn verify with -Dgroups=integration          │
│  7. Generate coverage     → JaCoCo reports                                 │
│  8. Upload artifacts      → Test results and coverage reports              │
│                                                                              │
│  DOCKER BUILD (matrix strategy)                                             │
│  ──────────────────────────────                                             │
│  strategy:                                                                  │
│    matrix:                                                                  │
│      service: [identity, payment, api-gateway, bank-simulator]             │
│                                                                              │
│  Creates 4 parallel jobs, one per service                                  │
│  Each builds and pushes its own Docker image                               │
│                                                                              │
│  SECURITY SCAN                                                              │
│  ─────────────                                                              │
│  Trivy scans each Docker image for vulnerabilities                         │
│  Results uploaded to GitHub Security tab                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Frontend CI Workflow

### Step 5.1: Frontend CI

**File: `.github/workflows/ci-frontend.yml`**

```yaml
name: CI - Frontend Dashboard

on:
  push:
    branches: [main, develop]
    paths:
      - 'frontend-dashboard/**'
  pull_request:
    branches: [main]
    paths:
      - 'frontend-dashboard/**'

env:
  NODE_VERSION: '18'
  WORKING_DIR: frontend-dashboard

jobs:
  lint-and-build:
    name: Lint & Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ${{ env.WORKING_DIR }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'
          cache-dependency-path: ${{ env.WORKING_DIR }}/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Run linter
        run: npm run lint

      - name: Build application
        run: npm run build
        env:
          VITE_API_URL: ${{ vars.VITE_API_URL || 'https://api.payflow.example.com' }}

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: frontend-build
          path: ${{ env.WORKING_DIR }}/dist
          retention-days: 7

  deploy:
    name: Deploy to S3
    needs: lint-and-build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'

    permissions:
      id-token: write
      contents: read

    steps:
      - name: Download build artifacts
        uses: actions/download-artifact@v4
        with:
          name: frontend-build
          path: dist

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-arn: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION || 'eu-west-2' }}

      - name: Deploy to S3
        run: |
          aws s3 sync dist/ s3://${{ secrets.S3_BUCKET_NAME }} \
            --delete \
            --cache-control "public, max-age=31536000, immutable" \
            --exclude "index.html"

          # index.html should not be cached aggressively
          aws s3 cp dist/index.html s3://${{ secrets.S3_BUCKET_NAME }}/index.html \
            --cache-control "public, max-age=0, must-revalidate"

      - name: Invalidate CloudFront cache
        if: vars.CLOUDFRONT_DISTRIBUTION_ID != ''
        run: |
          aws cloudfront create-invalidation \
            --distribution-id ${{ vars.CLOUDFRONT_DISTRIBUTION_ID }} \
            --paths "/*"
```

---

## 6. Verification

### View Workflow Runs

1. Go to your GitHub repository
2. Click "Actions" tab
3. See workflow runs and their status

### Trigger a Workflow

```powershell
# Make a change to a backend file
echo "// comment" >> identity-service/src/main/java/com/payflow/identity/IdentityServiceApplication.java

# Commit and push
git add .
git commit -m "test: trigger CI workflow"
git push origin main
```

### Check Results

1. Click on the workflow run
2. Expand job steps to see logs
3. Download artifacts (test results, coverage)

---

## 7. File Structure

```
.github/
└── workflows/
    ├── ci-backend.yml    ← Backend CI (Java services)
    └── ci-frontend.yml   ← Frontend CI (React dashboard)
```

---

## 8. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ┌────────────────────────┬────────────────────────────────────────────┐   │
│  │  Concept               │  Implementation                            │   │
│  ├────────────────────────┼────────────────────────────────────────────┤   │
│  │  Path filters          │  Only run when relevant files change       │   │
│  │  Service containers    │  PostgreSQL + Redis for tests              │   │
│  │  Matrix strategy       │  Parallel builds per service               │   │
│  │  Job dependencies      │  needs: build-and-test                     │   │
│  │  Conditional runs      │  if: github.ref == 'refs/heads/main'       │   │
│  │  Artifact upload       │  Test results, coverage, build output      │   │
│  │  Security scanning     │  Trivy for container vulnerabilities       │   │
│  │  GHCR                  │  GitHub Container Registry for images      │   │
│  └────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│  Key Pattern: Use matrix strategy for parallel Docker builds.              │
│  Much faster than sequential builds!                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What to Remember |
|---------|------------------|
| **Triggers** | `on: push` and `on: pull_request` |
| **Path filters** | Run only when relevant code changes |
| **Services** | Databases for tests in ephemeral containers |
| **Matrix** | Parallel execution for multiple services |
| **Artifacts** | Upload test results for debugging |

---

## 9. Common Issues and Solutions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING GUIDE                                     │
│                                                                              │
│  Issue 1: "Database connection refused"                                    │
│  ──────────────────────────────────────                                     │
│  Cause:   Service container not ready when tests start                     │
│  Fix:     Use healthcheck options on service containers                    │
│           options: >-                                                       │
│             --health-cmd pg_isready                                        │
│             --health-interval 10s                                          │
│                                                                              │
│  Issue 2: "Permission denied pushing to GHCR"                              │
│  ────────────────────────────────────────────                               │
│  Cause:   Missing permissions block in job                                 │
│  Fix:     Add permissions: packages: write                                 │
│                                                                              │
│  Issue 3: "Workflow not triggering"                                        │
│  ────────────────────────────────                                           │
│  Cause:   Path filter not matching changed files                           │
│  Fix:     Check paths match your directory structure                       │
│           Note: paths are relative to repo root                            │
│                                                                              │
│  Issue 4: "Maven cache not working"                                        │
│  ─────────────────────────────────                                          │
│  Cause:   setup-java action needs cache: maven                             │
│  Fix:     Ensure setup-java has cache: maven option                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

The CI/CD pipeline is now configured to:
- Build and test on every push
- Build Docker images on main branch
- Scan for security vulnerabilities
- Deploy frontend to S3 (when configured)

Parts 20-24 cover AWS deployment, E2E testing, and Git workflows. These are optional for local development but important for production deployment.

**Continue to:** [part-20-aws-vpc-rds.md](./part-20-aws-vpc-rds.md) (optional - AWS setup)

Or skip to: [part-24-sprint-summary.md](./part-24-sprint-summary.md) (sprint recap)

---

**End of Sprint 1, Part 19**
