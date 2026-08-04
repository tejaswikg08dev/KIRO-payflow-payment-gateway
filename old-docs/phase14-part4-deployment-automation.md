# Phase 14 Part 4 — Deployment Automation (EC2 via SSH)

## Goal
- Create a GitHub Actions deployment workflow triggered after CI passes
- Deploy to EC2 using SSH with docker compose
- Implement zero-downtime rolling updates

## Key Concept

```
┌────────────────────────────────────────────────────────┐
│  Deployment Pipeline                                   │
│                                                        │
│  GitHub Actions                   EC2 Instance         │
│  ┌────────────┐                  ┌──────────────────┐  │
│  │ CI passes  │ ──SSH──────────► │ Pull new images  │  │
│  │ on main    │                  │ docker compose   │  │
│  │            │                  │   up -d          │  │
│  └────────────┘                  │ Health check     │  │
│                                  └──────────────────┘  │
└────────────────────────────────────────────────────────┘
```

## Prerequisites
- EC2 instance running with Docker installed
- SSH key pair configured as GitHub secret
- Repository secrets: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`

## Step-by-Step

### 1. Deploy Workflow (`.github/workflows/deploy.yml`)

```yaml
name: Deploy to Production

on:
  workflow_run:
    workflows: ["Backend CI", "Frontend CI"]
    types: [completed]
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    environment: production

    steps:
      - uses: actions/checkout@v4

      - name: Copy docker-compose to server
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          source: "docker-compose.yml,scripts/"
          target: "/opt/payflow"

      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /opt/payflow

            # Login to GHCR
            echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin

            # Pull latest images
            docker compose pull

            # Rolling update (one at a time)
            docker compose up -d --no-deps identity-service
            sleep 10
            docker compose up -d --no-deps payment-service
            sleep 10
            docker compose up -d --no-deps api-gateway
            docker compose up -d --no-deps merchant-portal hosted-checkout

            # Health check
            for i in {1..30}; do
              if curl -sf http://localhost:8080/actuator/health > /dev/null; then
                echo "Deployment successful!"
                exit 0
              fi
              echo "Waiting for health check... ($i/30)"
              sleep 5
            done

            echo "Health check failed!"
            docker compose logs --tail=50
            exit 1

      - name: Notify on failure
        if: failure()
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /opt/payflow
            echo "Rolling back to previous images..."
            docker compose down
            docker compose up -d
```

### 2. Production docker-compose Override (`docker-compose.prod.yml`)

```yaml
version: '3.8'

services:
  identity-service:
    image: ghcr.io/${GITHUB_REPOSITORY}/identity-service:latest
    build: !reset null
    restart: always

  payment-service:
    image: ghcr.io/${GITHUB_REPOSITORY}/payment-service:latest
    build: !reset null
    restart: always

  api-gateway:
    image: ghcr.io/${GITHUB_REPOSITORY}/api-gateway:latest
    build: !reset null
    restart: always

  merchant-portal:
    image: ghcr.io/${GITHUB_REPOSITORY}/merchant-portal:latest
    build: !reset null
    restart: always

  hosted-checkout:
    image: ghcr.io/${GITHUB_REPOSITORY}/hosted-checkout:latest
    build: !reset null
    restart: always
```

### 3. Server Setup Script (`scripts/setup-server.sh`)

```bash
#!/bin/bash
# Run once on EC2 to prepare the deployment target

# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Install Docker Compose plugin
sudo apt-get install -y docker-compose-plugin

# Create app directory
sudo mkdir -p /opt/payflow
sudo chown $USER:$USER /opt/payflow

# Configure log rotation
cat << 'EOF' | sudo tee /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF
sudo systemctl restart docker
```

## Verification

```bash
# After push to main:
# 1. Backend CI passes → triggers deploy
# 2. Check GitHub Actions → Deploy workflow shows green

# On EC2:
ssh ec2-user@<EC2_HOST>
docker compose ps    # All services running
curl localhost:8080/actuator/health   # {"status":"UP"}
curl localhost:3000  # Merchant portal loads

# Check deployment logs
docker compose logs --since=5m api-gateway
```

## Git Commit

```bash
git add .github/workflows/deploy.yml docker-compose.prod.yml scripts/setup-server.sh
git commit -m "ci: add deployment automation via SSH to EC2"
```

## Next Step
→ **Phase 15 Part 2** — AWS VPC, subnets, and security groups
