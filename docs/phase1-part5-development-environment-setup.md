# Phase 1 — Part 5: Development Environment Setup

> Step-by-step installation of all tools needed for this project.
> Follow each step in order. Verify after each installation.

---

## 1. Install Java 17 (JDK)

**What:** Java Development Kit — needed to write and run Java code.
**Version:** 17 (LTS — Long Term Support, used by most companies)

**Step 1:** Download Eclipse Temurin JDK 17:
- Go to: https://adoptium.net/temurin/releases/?version=17
- Select: Operating System = Windows, Architecture = x64
- Download: .msi installer
- Run the installer with default options

**Step 2:** Verify installation — open Command Prompt (cmd):
```cmd
java -version
```
**Expected output:**
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x+x (build 17.0.x+x)
OpenJDK 64-Bit Server VM Temurin-17.0.x+x (build 17.0.x+x, mixed mode)
```

**Step 3:** Verify javac (compiler):
```cmd
javac -version
```
**Expected:** `javac 17.0.x`

**If not working:** Make sure JAVA_HOME is set:
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
```

---

## 2. Install Apache Maven 3.9+

**What:** Build tool for Java — compiles code, manages dependencies, runs tests.
**Version:** 3.9.x (latest stable)

**Step 1:** Download:
- Go to: https://maven.apache.org/download.cgi
- Download: apache-maven-3.9.x-bin.zip (Binary zip archive)

**Step 2:** Extract to `C:\tools\apache-maven-3.9.x`

**Step 3:** Add to PATH:
- Right-click "This PC" → Properties → Advanced system settings
- Environment Variables → System variables → Path → Edit
- Add: `C:\tools\apache-maven-3.9.x\bin`

**Step 4:** Verify:
```cmd
mvn -version
```
**Expected:**
```
Apache Maven 3.9.x
Maven home: C:\tools\apache-maven-3.9.x
Java version: 17.0.x, vendor: Eclipse Adoptium
```

---

## 3. Install Node.js 18+ (For React Frontend)

**What:** JavaScript runtime — needed for React frontend development.
**Version:** 18.x or 20.x (LTS)

**Step 1:** Download:
- Go to: https://nodejs.org/
- Click "LTS" version (18.x or 20.x)
- Download .msi installer

**Step 2:** Run installer with defaults (includes npm)

**Step 3:** Verify:
```cmd
node --version
npm --version
```
**Expected:** `v18.x.x` or `v20.x.x` and `9.x.x` or `10.x.x`

---

## 4. Install Docker Desktop

**What:** Runs containers — we use this for PostgreSQL, Redis, DynamoDB, and all services.
**Why:** Instead of installing PostgreSQL, Redis etc. on your machine, we run them in containers.

**Step 1:** Download:
- Go to: https://www.docker.com/products/docker-desktop/
- Download Docker Desktop for Windows

**Step 2:** Run installer (may need restart, enables WSL2/Hyper-V)

**Step 3:** Open Docker Desktop, let it start

**Step 4:** Verify:
```cmd
docker --version
docker compose version
```
**Expected:**
```
Docker version 24.x.x or 25.x.x
Docker Compose version v2.x.x
```

**Step 5:** Test Docker works:
```cmd
docker run hello-world
```
Should print "Hello from Docker!"

---

## 5. Install Git

**What:** Version control — track code changes, push to GitHub.

**Step 1:** Download:
- Go to: https://git-scm.com/downloads
- Download for Windows

**Step 2:** Run installer:
- Default options are fine
- Choose "Git from the command line and also from 3rd-party software"

**Step 3:** Verify:
```cmd
git --version
```
**Expected:** `git version 2.x.x`

**Step 4:** Configure Git (run once):
```cmd
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

---

## 6. Install IDE

### Option A: IntelliJ IDEA Community Edition (Recommended for Java)

**Step 1:** Download:
- Go to: https://www.jetbrains.com/idea/download/
- Download Community Edition (FREE)

**Step 2:** Install with defaults

**Plugins to install after opening:**
- Lombok (for @Data, @Builder annotations)
- Spring Boot (may be pre-installed)
- Docker

### Option B: VS Code (If you prefer)

**Step 1:** Already installed if using Kiro

**Extensions to install:**
- Extension Pack for Java (Microsoft)
- Spring Boot Extension Pack (VMware)
- Docker (Microsoft)
- Thunder Client or REST Client (API testing)
- ESLint + Prettier (frontend)
- Tailwind CSS IntelliSense

---

## 7. Install Postman

**What:** GUI tool for testing REST APIs — send requests, see responses.

**Step 1:** Download:
- Go to: https://www.postman.com/downloads/
- Download for Windows

**Step 2:** Install and create free account

**We'll use Postman for:**
- Testing each service's endpoints
- Creating collections (saved requests)
- Running automated API tests
- Exporting collections for the team

---

## 8. Install AWS CLI v2

**What:** Command-line tool to interact with AWS services.

**Step 1:** Download:
- Go to: https://aws.amazon.com/cli/
- Download AWS CLI MSI installer for Windows

**Step 2:** Run installer

**Step 3:** Verify:
```cmd
aws --version
```
**Expected:** `aws-cli/2.x.x Python/3.x.x Windows/10`

**Step 4:** Configure (do this after creating AWS account):
```cmd
aws configure
```
Enter:
- Access Key ID: (from AWS Console → IAM → Your User → Security credentials)
- Secret Access Key: (same place)
- Default region: ap-south-1 (Mumbai, closest to India)
- Output format: json

---

## 9. Verify Everything Works Together

Run this checklist:

```cmd
java -version       ← Should show 17.x
mvn -version        ← Should show 3.9.x
node --version      ← Should show 18.x or 20.x
npm --version       ← Should show 9.x or 10.x
docker --version    ← Should show 24.x or 25.x
git --version       ← Should show 2.x.x
aws --version       ← Should show 2.x.x
```

If ALL show correct versions → your environment is ready!

---

## 10. Quick Docker Test (Run PostgreSQL + Redis)

Let's verify Docker can run databases we'll need:

```cmd
docker run -d --name test-postgres -e POSTGRES_PASSWORD=test123 -p 5432:5432 postgres:15
docker run -d --name test-redis -p 6379:6379 redis:7
```

Verify they're running:
```cmd
docker ps
```

Should show both containers. Clean up:
```cmd
docker stop test-postgres test-redis
docker rm test-postgres test-redis
```

---

## Next Step

→ Continue to **`phase1-part6-project-structure-and-git-setup.md`**
