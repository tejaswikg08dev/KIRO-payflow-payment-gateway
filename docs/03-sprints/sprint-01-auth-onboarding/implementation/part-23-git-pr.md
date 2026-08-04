# Sprint 1, Part 23: Git & Pull Request

**Duration:** 1-2 hours  
**Prerequisites:** Part 22 completed, All code working  
**Status:** 📘 WORKFLOW GUIDE (Generic best practices)

> **Note:** This part documents Git best practices and PR workflow. It's a conceptual guide that applies to any project.

---

## 1. What We're Building

In this part, you'll commit Sprint 1 code and create a **pull request**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     GIT WORKFLOW                                             │
│                                                                              │
│  Local Development                                                          │
│        │                                                                     │
│        │ git checkout -b feature/sprint-1-auth                              │
│        ▼                                                                     │
│  Feature Branch                                                             │
│        │                                                                     │
│        │ git add . && git commit                                            │
│        ▼                                                                     │
│  Commits:                                                                   │
│  ├── feat(infra): add service registry and config server                   │
│  ├── feat(gateway): add API gateway with rate limiting                     │
│  ├── feat(identity): add JWT authentication                                │
│  ├── feat(merchant): add merchant service with API keys                    │
│  ├── feat(frontend): add React merchant portal                             │
│  ├── feat(docker): add Dockerfiles and compose                             │
│  └── feat(ci): add GitHub Actions pipeline                                 │
│        │                                                                     │
│        │ git push -u origin feature/sprint-1-auth                          │
│        ▼                                                                     │
│  GitHub                                                                     │
│        │                                                                     │
│        │ Create Pull Request                                                │
│        ▼                                                                     │
│  PR: Sprint 1 - Authentication & Onboarding                                 │
│  └── develop branch                                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Conventional Commits

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMMIT MESSAGE FORMAT                                     │
│                                                                              │
│  <type>(<scope>): <description>                                             │
│                                                                              │
│  Types:                                                                     │
│  ──────                                                                     │
│  feat     → New feature                                                     │
│  fix      → Bug fix                                                         │
│  docs     → Documentation only                                              │
│  style    → Formatting (no code change)                                     │
│  refactor → Code restructuring                                              │
│  test     → Adding tests                                                    │
│  chore    → Maintenance tasks                                               │
│                                                                              │
│  Examples:                                                                   │
│  ─────────                                                                  │
│  feat(identity): add JWT token generation                                   │
│  fix(gateway): resolve CORS preflight issue                                 │
│  docs(readme): update installation steps                                    │
│  test(merchant): add API key generation tests                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Create Feature Branch

```powershell
# Ensure you're on develop
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/sprint-1-auth-onboarding
```


### Step 3.2: Stage and Commit

```powershell
# Check status
git status

# Add all changes
git add .

# Commit with meaningful message
git commit -m "feat: implement Sprint 1 - Authentication & Merchant Onboarding

- Add Service Registry (Eureka) for service discovery
- Add Config Server for centralized configuration
- Add API Gateway with rate limiting and correlation IDs
- Add Identity Service with JWT authentication
- Add Merchant Service with API key generation
- Add React Merchant Portal with login/register
- Add Docker configuration for all services
- Add GitHub Actions CI/CD pipeline
- Add E2E tests for user onboarding flow

Closes #1"
```


### Step 3.3: Push to Remote

```powershell
# Push and set upstream
git push -u origin feature/sprint-1-auth-onboarding
```


### Step 3.4: Create Pull Request

**On GitHub:**

1. Navigate to repository
2. Click "Compare & pull request"
3. Fill in PR template:

```markdown
## Summary
Sprint 1 implementation of Authentication and Merchant Onboarding.

## Changes
- 🏗️ Service Registry (Eureka)
- ⚙️ Config Server
- 🚪 API Gateway
- 🔐 Identity Service (JWT auth)
- 🏪 Merchant Service (API keys)
- 💻 React Merchant Portal
- 🐳 Docker configuration
- 🔄 CI/CD pipeline

## Testing
- [ ] All unit tests pass
- [ ] E2E tests pass
- [ ] Manual testing complete

## Screenshots
[Add login page screenshot]
```

---

## 4. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Feature branch** | Isolate work from main |
| **Conventional commits** | type(scope): description |
| **Pull request** | Code review before merge |

---

## 5. Next Steps

**Continue to:** [part-24-sprint-summary.md](./part-24-sprint-summary.md)

---

**End of Sprint 1, Part 23**
