# Sprint 0, Part 04: Git & Documentation

**Duration:** 1-2 hours  
**Prerequisites:** Parts 01-03 completed, Git installed

---

## 1. What We're Building

In this final part of Sprint 0, you'll:
- Create `.gitignore` to exclude build artifacts
- Update `README.md` with project documentation
- Initialize Git repository
- Create initial commit

---

## 2. Step-by-Step Implementation

### Step 2.1: Create .gitignore

Create `.gitignore` in the project root:

```gitignore
# ═══════════════════════════════════════════════════════════════════════════
# PayFlow .gitignore
# Files and folders that should NOT be committed to Git
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Maven build output
# ─────────────────────────────────────────────────────────────────────────────
target/
*.jar
*.war
*.ear

# ─────────────────────────────────────────────────────────────────────────────
# IDE files
# ─────────────────────────────────────────────────────────────────────────────
# IntelliJ IDEA
.idea/
*.iml
*.ipr
*.iws

# VS Code
.vscode/
*.code-workspace

# Eclipse
.project
.classpath
.settings/

# ─────────────────────────────────────────────────────────────────────────────
# Node.js (for frontend)
# ─────────────────────────────────────────────────────────────────────────────
node_modules/
npm-debug.log
yarn-error.log
.pnpm-debug.log

# ─────────────────────────────────────────────────────────────────────────────
# Environment and secrets
# ─────────────────────────────────────────────────────────────────────────────
.env
.env.local
.env.*.local
*.env
*.secrets
application-local.yml
application-local.properties

# ─────────────────────────────────────────────────────────────────────────────
# Operating system files
# ─────────────────────────────────────────────────────────────────────────────
.DS_Store
Thumbs.db
*.swp
*.swo
*~

# ─────────────────────────────────────────────────────────────────────────────
# Logs
# ─────────────────────────────────────────────────────────────────────────────
*.log
logs/

# ─────────────────────────────────────────────────────────────────────────────
# Docker
# ─────────────────────────────────────────────────────────────────────────────
# Don't ignore docker-compose files, but ignore local overrides
docker-compose.override.yml

# ─────────────────────────────────────────────────────────────────────────────
# Test output
# ─────────────────────────────────────────────────────────────────────────────
test-output/
coverage/
*.lcov
```

---

### Step 2.2: Create README.md

Create `README.md` in the project root:

```markdown
# PayFlow Payment Gateway

A production-ready payment gateway platform built with Spring Boot microservices.

## 🚀 Quick Start

### Prerequisites

- Java 17
- Maven 3.9+
- Docker Desktop
- Node.js 18+ (for frontend)

### Start Infrastructure

```bash
# Start PostgreSQL, Redis, DynamoDB Local, LocalStack
docker compose -f docker-compose-infra.yml up -d
```

### Build Project

```bash
mvn clean install
```

### Run Services

```bash
# Each service runs on a different port
cd service-registry && mvn spring-boot:run  # Port 8761
cd config-server && mvn spring-boot:run     # Port 8888
cd api-gateway && mvn spring-boot:run       # Port 8080
# ... more services
```

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                         (Port 8080)                              │
└───────────────────────────────┬─────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐       ┌───────────────┐       ┌───────────────┐
│   Identity    │       │   Payment     │       │   Merchant    │
│   Service     │       │   Service     │       │   Service     │
│   (8081)      │       │   (8083)      │       │   (8082)      │
└───────────────┘       └───────────────┘       └───────────────┘
```

## 📁 Project Structure

```
payflow-payment-gateway/
├── common-lib/          # Shared library
├── service-registry/    # Eureka server
├── config-server/       # Config server
├── api-gateway/         # API Gateway
├── identity-service/    # Authentication
├── merchant-service/    # Merchant management
├── payment-service/     # Payment processing
├── routing-service/     # Bank routing
├── settlement-service/  # Settlement batch
├── webhook-service/     # Webhook delivery
├── notification-service/# Email/SMS
├── bank-simulator/      # Bank mock
├── merchant-portal/     # React dashboard
└── hosted-checkout/     # React checkout
```

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3, Spring Cloud |
| Frontend | React 18, TypeScript, Vite |
| Database | PostgreSQL, Redis, DynamoDB |
| Messaging | AWS SQS, SNS |
| Protocol | ISO 8583 |
| DevOps | Docker, GitHub Actions, AWS |

## 📚 Documentation

See the `docs/` folder for complete documentation.

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

MIT License
```



---

### Step 2.3: Create CONTRIBUTING.md

Create `CONTRIBUTING.md` in the project root:

```markdown
# Contributing to PayFlow

Thank you for your interest in contributing to PayFlow!

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR-USERNAME/payflow-payment-gateway.git
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Workflow

### Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/description` | `feature/add-upi-payments` |
| Bugfix | `bugfix/description` | `bugfix/fix-auth-token` |
| Hotfix | `hotfix/description` | `hotfix/payment-timeout` |

### Commit Message Format

Use clear, descriptive commit messages:

```
type(scope): description

[optional body]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Formatting (no code change)
- `refactor`: Code restructuring
- `test`: Adding tests
- `chore`: Maintenance

**Examples:**
```
feat(payment): add card tokenization support
fix(auth): resolve JWT expiration issue
docs(readme): update installation steps
```

### Pull Request Guidelines

1. Update documentation if needed
2. Add tests for new features
3. Ensure all tests pass: `mvn test`
4. Keep PRs focused and small
5. Reference related issues

## Code Style

### Java
- Follow Google Java Style Guide
- Use meaningful variable names
- Add Javadoc for public methods

### React/TypeScript
- Use functional components with hooks
- Follow Airbnb style guide
- Use TypeScript strictly (no `any`)

## Testing

```bash
# Run all backend tests
mvn test

# Run frontend tests
cd merchant-portal && npm test
```

## Questions?

Open an issue or discussion on GitHub.
```

---

### Step 2.4: Initialize Git Repository

Now let's initialize Git and make the first commit.

**Open PowerShell and run these commands:**

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 1: Navigate to project folder
# ═══════════════════════════════════════════════════════════════════════════
cd C:\payflow-payment-gateway

# ═══════════════════════════════════════════════════════════════════════════
# Step 2: Initialize Git repository
# ═══════════════════════════════════════════════════════════════════════════
git init

# What this does:
# - Creates a hidden .git folder in your project
# - This folder stores all version history
# - Your project is now a Git repository!
```

**Expected output:**
```
Initialized empty Git repository in C:/payflow-payment-gateway/.git/
```

---

### Step 2.5: Configure Git (if not done)

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Configure your name and email (one-time setup)
# ═══════════════════════════════════════════════════════════════════════════
git config user.name "Your Name"
git config user.email "your.email@example.com"

# Verify configuration
git config user.name
git config user.email
```

---

### Step 2.6: Check Status

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 3: Check what files Git sees
# ═══════════════════════════════════════════════════════════════════════════
git status

# What you'll see:
# - Untracked files (files Git doesn't know about yet)
# - .gitignore patterns will already be applied
```

**Expected output:**
```
On branch master

No commits yet

Untracked files:
  (use "git add <file>..." to include in what will be committed)
        .gitignore
        CONTRIBUTING.md
        README.md
        common-lib/
        docker-compose-infra.yml
        docker/
        pom.xml

nothing added to commit but untracked files present
```

Notice that `target/`, `.idea/`, `node_modules/` are NOT listed because `.gitignore` excludes them!

---

### Step 2.7: Stage All Files

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 4: Stage all files for commit
# ═══════════════════════════════════════════════════════════════════════════
git add .

# What this does:
# - Adds all files to the "staging area"
# - Staging area = files ready to be committed
# - The . means "all files in current directory"
```

**Verify staging:**
```powershell
git status

# Now files should show as "Changes to be committed" (green text)
```

---

### Step 2.8: Create Initial Commit

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 5: Create the initial commit
# ═══════════════════════════════════════════════════════════════════════════
git commit -m "feat: initial project setup - Sprint 0 complete

- Add parent pom.xml with Spring Boot 3.2.x
- Add common-lib module with DTOs and exceptions
- Add docker-compose-infra.yml (PostgreSQL, Redis, LocalStack)
- Add database init scripts
- Add .gitignore for Java/Node projects
- Add README and CONTRIBUTING docs"

# What this does:
# - Creates a snapshot of all staged files
# - The -m flag lets you add a message inline
# - Good commit messages explain WHAT and WHY
```

**Expected output:**
```
[master (root-commit) abc1234] feat: initial project setup - Sprint 0 complete
 15 files changed, 850 insertions(+)
 create mode 100644 .gitignore
 create mode 100644 CONTRIBUTING.md
 create mode 100644 README.md
 create mode 100644 common-lib/pom.xml
 create mode 100644 common-lib/src/...
 ...
```

---

### Step 2.9: Create Develop Branch

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 6: Create and switch to develop branch
# ═══════════════════════════════════════════════════════════════════════════
git branch develop
git checkout develop

# Or in one command:
# git checkout -b develop

# Why develop branch?
# - main/master = stable, production-ready code
# - develop = ongoing development work
# - Feature branches merge into develop first
```

**Verify branch:**
```powershell
git branch

# Output (asterisk shows current branch):
#   develop
# * master
```

---

### Step 2.10: View Git History

```powershell
# ═══════════════════════════════════════════════════════════════════════════
# Step 7: View commit history
# ═══════════════════════════════════════════════════════════════════════════
git log --oneline

# Output:
# abc1234 feat: initial project setup - Sprint 0 complete

# More detailed view:
git log

# Shows full commit info (author, date, full message)
```

---

## 3. Concepts Deep Dive

### 3.1 Why Version Control?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         WITHOUT GIT                                          │
│                                                                              │
│   project_v1/                                                                │
│   project_v2/                                                                │
│   project_v2_backup/                                                         │
│   project_final/                                                             │
│   project_final_FINAL/                                                       │
│   project_REALLY_final/                                                      │
│                                                                              │
│   😱 Chaos! Which is the real version?                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                         WITH GIT                                             │
│                                                                              │
│   project/                                                                   │
│   └── .git/  (contains ALL history)                                         │
│                                                                              │
│   git log:                                                                   │
│   ├── commit #5: "Add payment feature"                                       │
│   ├── commit #4: "Fix login bug"                                             │
│   ├── commit #3: "Add user auth"                                             │
│   ├── commit #2: "Setup database"                                            │
│   └── commit #1: "Initial commit"                                            │
│                                                                              │
│   ✅ Clean! One folder, full history                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Git Workflow Visualization

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        The Three Git Areas                                   │
│                                                                              │
│  ┌─────────────────┐    git add     ┌─────────────────┐    git commit       │
│  │                 │ ─────────────> │                 │ ─────────────>      │
│  │  Working        │                │   Staging       │                     │
│  │  Directory      │                │   Area          │    Repository       │
│  │                 │ <───────────── │                 │ <───────────        │
│  │  (your files)   │   git restore  │  (ready to      │   (saved            │
│  │                 │                │   commit)       │    history)         │
│  └─────────────────┘                └─────────────────┘                     │
│                                                                              │
│  Example:                                                                    │
│  1. Edit UserService.java        → Changes in Working Directory             │
│  2. git add UserService.java     → Moved to Staging Area                    │
│  3. git commit -m "Add user"     → Saved in Repository                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Branching Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PayFlow Branching Strategy                            │
│                                                                              │
│  main (production)                                                           │
│  ──●────────●────────●────────●─────────────> stable releases               │
│              \      /          \                                             │
│               \    /            \                                            │
│  develop       ●──●──●──●──●──●──●──●────────> ongoing development          │
│                 \    /     \     /                                           │
│                  \  /       \   /                                            │
│  feature/auth     ●──●       \ /                                             │
│                               ●                                              │
│  feature/payment              └──●──●                                        │
│                                                                              │
│  Legend:                                                                     │
│  ● = commit                                                                  │
│  Lines = branch history                                                      │
│  Merge = branches coming together                                            │
│                                                                              │
│  Workflow:                                                                   │
│  1. Create feature branch from develop                                       │
│  2. Work on feature, make commits                                            │
│  3. Merge feature into develop                                               │
│  4. When ready, merge develop into main                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.4 Understanding .gitignore Patterns

| Pattern | Meaning | Example |
|---------|---------|---------|
| `target/` | Folder named "target" | Ignores Maven build output |
| `*.jar` | Any file ending in .jar | Ignores all JAR files |
| `!important.jar` | Exception (don't ignore) | Keep this specific JAR |
| `.idea/` | Folder named ".idea" | Ignores IntelliJ settings |
| `*.log` | Any file ending in .log | Ignores all log files |
| `.env*` | Files starting with .env | Ignores .env, .env.local |

---

## 4. Verification

### 4.1 Verify Git Setup

```powershell
# Check repository status
git status
# Expected: "On branch develop, nothing to commit, working tree clean"

# Check commit history
git log --oneline
# Expected: Shows at least one commit

# Check branches
git branch
# Expected: Shows "develop" and "main/master"

# Check .gitignore is working
mkdir target
echo "test" > target/test.txt
git status
# Expected: target/ should NOT appear (it's ignored)
rmdir /s /q target
```

### 4.2 Verification Checklist

| Check | Command | Expected Result |
|-------|---------|-----------------|
| Repository initialized | `git status` | Shows branch info, no errors |
| Initial commit exists | `git log --oneline` | At least 1 commit shown |
| develop branch created | `git branch` | Shows develop branch |
| .gitignore working | Create target/, check status | target/ not shown |

---

## 5. File Structure After This Part

```
payflow-payment-gateway/
├── .git/                       ← NEW! Git repository data
├── .gitignore                  ← NEW! Ignore rules
├── CONTRIBUTING.md             ← NEW! Contribution guidelines
├── README.md                   ← NEW! Project overview
├── pom.xml
├── docker-compose-infra.yml
├── docker/
│   ├── init-db.sql
│   └── init-localstack.sh
└── common-lib/
    ├── pom.xml
    └── src/
        └── ...
```

---

## 6. Key Takeaways

### Git Fundamentals

| Concept | What It Means | Why It Matters |
|---------|---------------|----------------|
| Repository | Project folder with .git | Tracks all changes |
| Commit | Snapshot of files | Can always go back |
| Branch | Parallel version | Safe experimentation |
| Staging | Prepare for commit | Choose what to save |

### Essential Git Commands

| Command | Purpose | Example |
|---------|---------|---------|
| `git init` | Create repository | First time setup |
| `git add .` | Stage all changes | Prepare to commit |
| `git commit -m "msg"` | Save snapshot | Create checkpoint |
| `git status` | Check state | See what changed |
| `git log` | View history | See past commits |
| `git branch` | List branches | See all versions |
| `git checkout` | Switch branch | Move between versions |

### Best Practices

1. **Commit often**: Small, focused commits are easier to understand
2. **Write good messages**: "Fix login bug" not "fixed stuff"
3. **Use .gitignore**: Never commit build artifacts or secrets
4. **Branch for features**: Keep main/develop stable
5. **Pull before push**: Stay in sync with team

---

## 7. Common Issues & Solutions

### Issue 1: "Not a git repository"

```powershell
# Symptom:
fatal: not a git repository (or any of the parent directories)

# Solution:
# You're in the wrong folder, or git init wasn't run
cd C:\payflow-payment-gateway
git init
```

### Issue 2: Files not being ignored

```powershell
# Symptom:
# .gitignore exists but target/ still shows in git status

# Solution:
# Files already tracked won't be ignored. Remove from cache:
git rm -r --cached target/
git commit -m "chore: remove target from tracking"
```

### Issue 3: Wrong email in commits

```powershell
# Solution: Update git config
git config user.email "correct.email@example.com"

# To fix last commit:
git commit --amend --reset-author
```

### Issue 4: Accidentally committed secrets

```powershell
# If you committed .env or credentials:
# 1. Add to .gitignore
# 2. Remove from tracking:
git rm --cached .env
git commit -m "chore: remove secrets from tracking"

# 3. IMPORTANT: Change your passwords/keys immediately!
# Git history still contains the old file
```

---

## 8. Related Concepts

| Topic | What to Learn | When Needed |
|-------|---------------|-------------|
| Git Remote | Push/pull to GitHub | Team collaboration |
| Merge vs Rebase | Combining branches | Complex workflows |
| Git Hooks | Auto-run scripts | CI/CD integration |
| Pull Requests | Code review | Team development |
| Git Tags | Version releases | Production releases |

---

## 9. Sprint 0 Complete Summary

### What You've Built

| Part | Deliverable | Status |
|------|-------------|--------|
| Part 01 | Maven multi-module project | ✅ |
| Part 02 | Docker infrastructure | ✅ |
| Part 03 | Common library | ✅ |
| Part 04 | Git repository & docs | ✅ |

### Final Verification

```powershell
# Build entire project
mvn clean install
# Expected: BUILD SUCCESS

# Check Docker containers
docker ps
# Expected: 3 containers (postgres, redis, localstack)

# Check Git
git log --oneline
# Expected: Initial commit
```

### Project State

```
✅ Parent POM configured
✅ common-lib module ready
✅ PostgreSQL running with 4 schemas
✅ Redis running for caching
✅ LocalStack running for AWS emulation
✅ Git repository initialized
✅ Documentation in place
```

---

## 10. Next Steps

**Congratulations! Sprint 0 is complete!** 🎉

You now have:
- A properly structured Maven project
- Infrastructure running in Docker
- A shared library ready for use
- Version control with Git

**Continue to:** [Sprint 1: Auth & Onboarding](../../sprint-01-auth-onboarding/requirements.md)

In Sprint 1, you'll build:
- Service Registry (Eureka)
- Config Server
- API Gateway
- Identity Service (JWT authentication)
- Merchant Service
- React frontend (login/register)

---

**End of Sprint 0, Part 04**

*Sprint 0 Complete! You're ready for Sprint 1*
