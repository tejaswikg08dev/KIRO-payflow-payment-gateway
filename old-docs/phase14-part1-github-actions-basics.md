# Hands-On Guide — Phase 14 Part 1: GitHub Actions Basics

## Goal
- Understanding of CI/CD concepts
- GitHub Actions workflow file structure
- Trigger events (push, PR)
- Jobs, steps, actions

---

## What Is CI/CD?

```
CI (Continuous Integration):
├── Developer pushes code to GitHub
├── Automatically: build + test
├── If tests fail → developer notified immediately
└── "Is this code safe to merge?" → YES/NO

CD (Continuous Deployment):
├── After CI passes: automatically deploy
├── Build Docker image → push to ECR → deploy to EC2
└── New version running in production within minutes

OUR PIPELINE:
  Push to main → Build Java → Run tests → Build Docker → Push to ECR → Deploy to EC2
```

---

## Workflow File Structure

```yaml
# .github/workflows/ci-backend.yml

name: Backend CI/CD                    # Workflow name (shown in GitHub UI)

on:                                     # WHEN to run
  push:
    branches: [main, develop]           # On push to main or develop
  pull_request:
    branches: [main]                    # On PR targeting main

jobs:                                   # WHAT to do
  build-and-test:                       # Job name
    runs-on: ubuntu-latest              # Machine type (GitHub-hosted runner)
    
    steps:                              # Sequential steps in this job
      - name: Checkout code
        uses: actions/checkout@v4       # Clone the repository

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Run tests
        run: mvn test

      - name: Build Docker images
        run: |
          docker build -f identity-service/Dockerfile -t payflow/identity-service .
          docker build -f payment-service/Dockerfile -t payflow/payment-service .
```

---

## Key Concepts

| Concept | What It Means |
|---------|--------------|
| Workflow | Complete CI/CD pipeline (one YAML file) |
| Trigger (on:) | Event that starts the workflow (push, PR, schedule) |
| Job | A set of steps running on one machine |
| Step | One action (checkout, build, test, deploy) |
| Action | Reusable step (actions/checkout@v4, actions/setup-java@v4) |
| Secret | Hidden value (AWS keys) stored in GitHub Settings |
| Artifact | File produced by build (JAR, Docker image, test report) |

---

## Next Step → Phase 14 Parts 2-4 (backend pipeline, frontend pipeline, deployment)
