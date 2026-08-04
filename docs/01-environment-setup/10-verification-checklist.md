# Environment Setup — Verification Checklist

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

This is the final verification guide to ensure your entire development environment is correctly configured. You'll run a comprehensive checklist to verify all tools work together.

---

## 2. Complete Environment Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PayFlow Development Environment                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   YOUR WINDOWS MACHINE                                                       │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                                                                  │       │
│   │   DEVELOPMENT TOOLS                                             │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │ Java 17 │ Maven │ Node.js │ npm │ Git │ AWS CLI        │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   │                                                                  │       │
│   │   IDEs                                                          │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │ IntelliJ IDEA (Java)  │  VS Code (React, Docker)        │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   │                                                                  │       │
│   │   DOCKER DESKTOP (WSL 2)                                        │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │ PostgreSQL │ Redis │ LocalStack │ Bank Simulator        │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   │                                                                  │       │
│   │   TESTING                                                       │       │
│   │   ┌─────────────────────────────────────────────────────────┐   │       │
│   │   │ Postman │ Browser │ Terminal                            │   │       │
│   │   └─────────────────────────────────────────────────────────┘   │       │
│   │                                                                  │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                              │                                               │
│                              │ Deploy                                        │
│                              ▼                                               │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                         AWS CLOUD                                │       │
│   │   VPC │ EC2/ECS │ RDS │ ElastiCache │ DynamoDB │ S3 │ ...      │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Version Requirements

| Tool | Minimum Version | Recommended Version | Your Version |
|------|-----------------|---------------------|--------------|
| Java | 17.0.0 | 17.0.x (LTS) | ______ |
| Maven | 3.8.0 | 3.9.x | ______ |
| Node.js | 18.0.0 | 18.x or 20.x (LTS) | ______ |
| npm | 9.0.0 | 9.x or 10.x | ______ |
| Git | 2.40.0 | 2.42.x | ______ |
| Docker | 24.0.0 | 24.x | ______ |
| Docker Compose | 2.20.0 | 2.x | ______ |
| AWS CLI | 2.13.0 | 2.x | ______ |
| VS Code | 1.80.0 | Latest | ______ |
| IntelliJ IDEA | 2023.2 | Latest | ______ |
| Postman | 10.0.0 | Latest | ______ |

---

## 4. Step-by-Step Verification

### 4.1: Verify Java Installation

**Open PowerShell and run:**

```powershell
# Check Java version
java -version
```

**Expected Output:**
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x...
OpenJDK 64-Bit Server VM Temurin-17.0.x...
```

```powershell
# Check JAVA_HOME
echo $env:JAVA_HOME
```

**Expected:** Path to Java installation (e.g., `C:\Program Files\Eclipse Adoptium\jdk-17...`)

```powershell
# Test Java compilation
echo 'public class Test { public static void main(String[] args) { System.out.println("Java works!"); } }' > Test.java
javac Test.java
java Test
del Test.java Test.class
```

**Expected Output:** `Java works!`

**✅ Pass** / **❌ Fail** (circle one)

---

### 4.2: Verify Maven Installation

```powershell
# Check Maven version
mvn -version
```

**Expected Output:**
```
Apache Maven 3.9.x
Maven home: C:\apache-maven-3.9.x
Java version: 17.0.x, vendor: Eclipse Adoptium
```

```powershell
# Check MAVEN_HOME
echo $env:MAVEN_HOME
```

**Expected:** Path to Maven (e.g., `C:\apache-maven-3.9.x`)

**✅ Pass** / **❌ Fail**

---

### 4.3: Verify Node.js and npm

```powershell
# Check Node.js version
node -v
```

**Expected:** `v18.x.x` or `v20.x.x`

```powershell
# Check npm version
npm -v
```

**Expected:** `9.x.x` or `10.x.x`

```powershell
# Test Node.js execution
node -e "console.log('Node.js works!')"
```

**Expected Output:** `Node.js works!`

**✅ Pass** / **❌ Fail**

---

### 4.4: Verify Git Installation

```powershell
# Check Git version
git --version
```

**Expected:** `git version 2.42.x.windows.x`

```powershell
# Check Git config
git config --list
```

**Expected:** Should include `user.name` and `user.email`

```powershell
# Test SSH connection to GitHub
ssh -T git@github.com
```

**Expected:** `Hi username! You've successfully authenticated...`

**✅ Pass** / **❌ Fail**

---

### 4.5: Verify Docker Installation

```powershell
# Check Docker version
docker --version
```

**Expected:** `Docker version 24.x.x`

```powershell
# Check Docker Compose version
docker compose version
```

**Expected:** `Docker Compose version v2.x.x`

```powershell
# Test Docker is running
docker run hello-world
```

**Expected:** `Hello from Docker! This message shows that your installation appears to be working correctly.`

```powershell
# Test Docker Compose with PostgreSQL
docker run -d --name test-pg -e POSTGRES_PASSWORD=test -p 5432:5432 postgres:15-alpine
docker ps
docker stop test-pg
docker rm test-pg
```

**Expected:** Container starts and stops successfully

**✅ Pass** / **❌ Fail**

---

### 4.6: Verify AWS CLI

```powershell
# Check AWS CLI version
aws --version
```

**Expected:** `aws-cli/2.x.x Python/3.x.x Windows/10`

```powershell
# Verify AWS credentials
aws sts get-caller-identity
```

**Expected:**
```json
{
    "UserId": "...",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/payflow-admin"
}
```

**✅ Pass** / **❌ Fail**

---

### 4.7: Verify VS Code

```powershell
# Check VS Code version
code --version
```

**Expected:** Version number displayed

```powershell
# List installed extensions
code --list-extensions
```

**Expected:** Should include:
- `esbenp.prettier-vscode`
- `dbaeumer.vscode-eslint`
- `ms-azuretools.vscode-docker`

**✅ Pass** / **❌ Fail**

---

### 4.8: Verify IntelliJ IDEA

1. Open IntelliJ IDEA
2. Go to: **Help → About**
3. Verify version: 2023.2 or later
4. Go to: **File → Project Structure → SDKs**
5. Verify Java 17 is configured

**✅ Pass** / **❌ Fail**

---

### 4.9: Verify Postman

1. Open Postman
2. Create a new request:
   - Method: GET
   - URL: `https://jsonplaceholder.typicode.com/posts/1`
3. Click Send
4. Verify response with status 200

**✅ Pass** / **❌ Fail**

---

## 5. Integration Test

Now let's test all tools working together.

### 5.1: Clone and Run PayFlow

```powershell
# Navigate to your projects folder
cd ~\Projects  # or your preferred location

# Clone the PayFlow repository (if not already done)
# git clone https://github.com/your-repo/payflow-payment-gateway.git

# Navigate to project
cd payflow-payment-gateway

# Verify project structure
dir
```

**Expected:** Should see folders like `api-gateway`, `payment-service`, `merchant-portal`, etc.

### 5.2: Start Infrastructure with Docker

```powershell
# Start PostgreSQL and Redis
docker compose -f docker-compose-infra.yml up -d

# Verify containers are running
docker ps
```

**Expected:** PostgreSQL and Redis containers running

### 5.3: Build Backend with Maven

```powershell
# Build all services (skip tests for now)
mvn clean install -DskipTests
```

**Expected:** `BUILD SUCCESS` for all modules

### 5.4: Test Frontend Setup

```powershell
# Navigate to merchant portal
cd merchant-portal

# Install dependencies
npm install

# Verify it works (Ctrl+C to stop)
npm run dev
```

**Expected:** Vite dev server starts on http://localhost:5173

### 5.5: Open in IDEs

**IntelliJ IDEA:**
1. Open project folder
2. Wait for indexing to complete
3. Verify no red errors in Project view

**VS Code:**
1. Open `merchant-portal` folder
2. Verify no ESLint errors
3. Open a `.tsx` file, verify syntax highlighting

### 5.6: Clean Up

```powershell
# Stop Docker containers
docker compose -f docker-compose-infra.yml down

# Verify containers stopped
docker ps
```

---

## 6. Verification Summary

### Checklist

| # | Component | Status | Notes |
|---|-----------|--------|-------|
| 1 | Java 17 | ⬜ | |
| 2 | Maven | ⬜ | |
| 3 | Node.js | ⬜ | |
| 4 | npm | ⬜ | |
| 5 | Git | ⬜ | |
| 6 | Git SSH | ⬜ | |
| 7 | Docker | ⬜ | |
| 8 | Docker Compose | ⬜ | |
| 9 | AWS CLI | ⬜ | |
| 10 | VS Code | ⬜ | |
| 11 | VS Code Extensions | ⬜ | |
| 12 | IntelliJ IDEA | ⬜ | |
| 13 | IntelliJ JDK | ⬜ | |
| 14 | Postman | ⬜ | |
| 15 | PayFlow Clone | ⬜ | |
| 16 | PayFlow Build | ⬜ | |
| 17 | Frontend Dev | ⬜ | |

**Mark each with:**
- ✅ = Pass
- ❌ = Fail (see troubleshooting)
- ⏭️ = Skipped (with reason)

---

## 7. Common Issues & Fixes

### Java Issues

| Issue | Solution |
|-------|----------|
| `java not recognized` | Restart terminal, check PATH |
| Wrong Java version | Update JAVA_HOME, check PATH order |
| `JAVA_HOME not set` | Set environment variable manually |

### Maven Issues

| Issue | Solution |
|-------|----------|
| `mvn not recognized` | Add Maven `bin` to PATH |
| Download errors | Check internet, try again |
| `Could not find artifact` | Run `mvn clean install` in parent |

### Node.js Issues

| Issue | Solution |
|-------|----------|
| `node not recognized` | Restart terminal, reinstall Node.js |
| npm permission errors | Run as Administrator |
| Package install fails | Delete `node_modules`, retry |

### Docker Issues

| Issue | Solution |
|-------|----------|
| Docker daemon not running | Start Docker Desktop |
| WSL 2 error | Run `wsl --update` |
| Port already in use | Stop conflicting service |
| Out of disk space | `docker system prune` |

### Git Issues

| Issue | Solution |
|-------|----------|
| SSH permission denied | Regenerate SSH key, add to GitHub |
| `git not recognized` | Restart terminal, check PATH |
| Merge conflicts | Use VS Code merge editor |

### AWS Issues

| Issue | Solution |
|-------|----------|
| Access denied | Check IAM permissions |
| Invalid credentials | Re-run `aws configure` |
| Region error | Set correct region |

---

## 8. Environment Variables Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Required Environment Variables                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   VARIABLE              EXAMPLE VALUE                                        │
│   ──────────────────────────────────────────────────────────────────────    │
│   JAVA_HOME             C:\Program Files\Eclipse Adoptium\jdk-17.0.x        │
│   MAVEN_HOME            C:\apache-maven-3.9.x                               │
│   PATH                  %JAVA_HOME%\bin;%MAVEN_HOME%\bin;...                │
│                                                                              │
│   AWS_ACCESS_KEY_ID     AKIA...                                             │
│   AWS_SECRET_ACCESS_KEY wJalr...  (or use aws configure)                   │
│   AWS_DEFAULT_REGION    us-east-1                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PayFlow Development Quick Reference                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   START DEVELOPMENT SESSION:                                                 │
│   1. Open Docker Desktop (wait for startup)                                 │
│   2. docker compose -f docker-compose-infra.yml up -d                       │
│   3. Open IntelliJ → PayFlow project                                        │
│   4. Run service (Shift+F10)                                                │
│                                                                              │
│   START FRONTEND:                                                            │
│   1. cd merchant-portal                                                     │
│   2. npm run dev                                                            │
│   3. Open http://localhost:5173                                             │
│                                                                              │
│   TEST APIs:                                                                 │
│   1. Open Postman                                                           │
│   2. Select "PayFlow Local" environment                                     │
│   3. Run requests                                                           │
│                                                                              │
│   END SESSION:                                                               │
│   1. Stop running services (Ctrl+C)                                         │
│   2. docker compose -f docker-compose-infra.yml down                        │
│   3. git add . && git commit -m "message" && git push                       │
│                                                                              │
│   USEFUL COMMANDS:                                                           │
│   mvn clean install -DskipTests    # Build all                             │
│   mvn test                          # Run tests                             │
│   docker ps                         # List containers                       │
│   docker logs <container>           # View logs                             │
│   docker compose logs -f            # Follow all logs                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

**Congratulations! Your environment is ready! 🎉**

**You have completed:**
- ✅ Java 17 installation
- ✅ Maven installation
- ✅ Node.js installation
- ✅ Docker Desktop setup
- ✅ Git configuration
- ✅ IDE setup (VS Code + IntelliJ)
- ✅ Postman setup
- ✅ AWS account setup
- ✅ Full environment verification

---

## Where to Go Next

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Learning Path                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   YOU ARE HERE                                                               │
│        │                                                                     │
│        ▼                                                                     │
│   ┌─────────────────┐                                                        │
│   │ ✅ Environment  │                                                        │
│   │    Setup        │                                                        │
│   └────────┬────────┘                                                        │
│            │                                                                 │
│            ▼                                                                 │
│   ┌─────────────────┐     Read full architecture and APIs                   │
│   │ 02 - Master     │     before starting implementation                    │
│   │     Documents   │                                                        │
│   └────────┬────────┘                                                        │
│            │                                                                 │
│            ▼                                                                 │
│   ┌─────────────────┐     Start building!                                   │
│   │ 03 - Sprint 0   │     Foundation & Project Setup                        │
│   │     Foundation  │                                                        │
│   └────────┬────────┘                                                        │
│            │                                                                 │
│            ▼                                                                 │
│   ┌─────────────────┐     First real feature                                │
│   │ 03 - Sprint 1   │     Authentication & Onboarding                       │
│   │     Auth        │                                                        │
│   └────────┬────────┘                                                        │
│            │                                                                 │
│            ▼                                                                 │
│        ... more sprints ...                                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Continue to:** [../02-master-documents/requirements-complete.md](../02-master-documents/requirements-complete.md)

Take some time to read through the master documents to understand the full scope of PayFlow before diving into Sprint 0.

---

**End of Environment Setup**

*You're ready to start building PayFlow! 🚀*
