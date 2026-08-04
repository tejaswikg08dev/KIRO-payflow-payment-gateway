# Sprint 2, Part 13: Git & Pull Request

**Duration:** 1-2 hours  
**Prerequisites:** Part 12 completed, All code working  
**Status:** 📘 WORKFLOW GUIDE (Best practices)

> **Note:** This part documents Git best practices and PR workflow for Sprint 2.

---

## 1. What We're Building

In this part, you'll commit Sprint 2 code and create a **pull request**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     GIT WORKFLOW (SPRINT 2)                                  │
│                                                                              │
│  Local Development                                                          │
│        │                                                                     │
│        │ git checkout -b feature/sprint-2-api-key-management               │
│        ▼                                                                     │
│  Feature Branch                                                             │
│        │                                                                     │
│        │ git add . && git commit                                            │
│        ▼                                                                     │
│  Commits:                                                                   │
│  ├── feat(gateway): add ApiKeyAuthFilter for X-Api-Key auth                │
│  ├── feat(merchant): add API key generation endpoints                      │
│  ├── feat(merchant): add list and revoke API key endpoints                 │
│  ├── feat(merchant): add webhook configuration endpoints                   │
│  ├── feat(merchant): add internal validation endpoint                      │
│  ├── feat(frontend): add ApiKeysPage component                             │
│  ├── feat(frontend): update dashboard with API keys link                   │
│  ├── test(e2e): add API key management E2E tests                           │
│  └── docs: add Sprint 2 implementation documentation                       │
│        │                                                                     │
│        │ git push -u origin feature/sprint-2-api-key-management            │
│        ▼                                                                     │
│  GitHub                                                                     │
│        │                                                                     │
│        │ Create Pull Request                                                │
│        ▼                                                                     │
│  PR: Sprint 2 - API Key Management                                          │
│  └── develop branch                                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Sprint 2 Files Changed

### 2.1 New Files Created

| File | Description |
|------|-------------|
| `api-gateway/.../filter/ApiKeyAuthFilter.java` | API key authentication filter |
| `merchant-service/.../controller/InternalController.java` | Internal validation endpoint |
| `frontend-dashboard/src/pages/ApiKeysPage.tsx` | API keys management page |

### 2.2 Modified Files

| File | Changes |
|------|---------|
| `api-gateway/.../ApiGatewayApplication.java` | Added WebClient bean |
| `merchant-service/.../controller/MerchantController.java` | Added list, revoke, webhook endpoints |
| `merchant-service/.../service/MerchantService.java` | Added key management methods |
| `merchant-service/.../repository/ApiKeyRepository.java` | Added `findByMerchantId` |
| `frontend-dashboard/src/App.tsx` | Added `/api-keys` route |
| `frontend-dashboard/src/pages/DashboardPage.tsx` | Added "Manage API Keys" button |

---

## 3. Step-by-Step Implementation

### Step 3.1: Create Feature Branch

```powershell
# Ensure you're on develop
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/sprint-2-api-key-management
```

### Step 3.2: Review Changes

```powershell
# Check status
git status

# Review changes
git diff --stat
```

Expected output:

```
 api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java        |  12 ++
 api-gateway/src/main/java/com/payflow/gateway/filter/ApiKeyAuthFilter.java      | 180 +++++++++++++++++++
 merchant-service/src/main/java/com/payflow/merchant/controller/InternalController.java  |  45 +++++
 merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java  |  52 ++++++
 merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java        |  78 ++++++++
 merchant-service/src/main/java/com/payflow/merchant/repository/ApiKeyRepository.java   |   5 +
 frontend-dashboard/src/App.tsx                                                          |   5 +-
 frontend-dashboard/src/pages/ApiKeysPage.tsx                                           | 285 ++++++++++++++++++++++++++++++
 frontend-dashboard/src/pages/DashboardPage.tsx                                          |  12 ++
 9 files changed, 673 insertions(+), 1 deletion(-)
```

### Step 3.3: Stage and Commit (Atomic Commits)

**Option A: Single Comprehensive Commit**

```powershell
git add .
git commit -m "feat: implement Sprint 2 - API Key Management

- Add ApiKeyAuthFilter for X-Api-Key header authentication
- Add API key generation with SHA-256 hashing
- Add list/revoke API key endpoints
- Add webhook configuration endpoints
- Add internal validation endpoint for gateway
- Add ApiKeysPage React component
- Add Redis caching for key validation (5 min TTL)

Closes #2"
```

**Option B: Multiple Granular Commits (Preferred)**

```powershell
# Commit 1: API Gateway filter
cd api-gateway
git add src/main/java/com/payflow/gateway/filter/ApiKeyAuthFilter.java
git add src/main/java/com/payflow/gateway/ApiGatewayApplication.java
git commit -m "feat(gateway): add ApiKeyAuthFilter for X-Api-Key authentication

- Validate API keys in X-Api-Key header
- SHA-256 hash keys for secure storage lookup
- Cache validation results in Redis (5 min TTL)
- Add X-Merchant-Id header for downstream services
- Skip auth for public paths (/v1/auth, /actuator, /swagger-ui)"

# Commit 2: Merchant service - internal endpoint
cd ../merchant-service
git add src/main/java/com/payflow/merchant/controller/InternalController.java
git commit -m "feat(merchant): add internal API key validation endpoint

- POST /internal/validate-api-key for gateway use
- Returns merchantId and keyType on valid key
- Not exposed through API gateway"

# Commit 3: Merchant service - API key endpoints
git add src/main/java/com/payflow/merchant/controller/MerchantController.java
git add src/main/java/com/payflow/merchant/service/MerchantService.java
git add src/main/java/com/payflow/merchant/repository/ApiKeyRepository.java
git commit -m "feat(merchant): add API key management endpoints

- POST /v1/merchants/{id}/api-keys - generate key pair
- GET /v1/merchants/{id}/api-keys - list keys (secrets masked)
- DELETE /v1/merchants/{id}/api-keys/{keyId} - revoke key"

# Commit 4: Merchant service - webhook endpoints
git commit -m "feat(merchant): add webhook configuration endpoints

- PUT /v1/merchants/{id}/webhook - update webhook URL
- GET /v1/merchants/{id}/webhook - get webhook config
- Regenerate webhook secret on URL update"

# Commit 5: Frontend
cd ../frontend-dashboard
git add src/pages/ApiKeysPage.tsx
git add src/App.tsx
git add src/pages/DashboardPage.tsx
git commit -m "feat(frontend): add API Keys management page

- Add ApiKeysPage component with key generation
- Add key list table with revoke functionality
- Add webhook URL configuration
- Add navigation from dashboard"

# Commit 6: Documentation
cd ..
git add docs/03-sprints/sprint-02-api-key-management/
git commit -m "docs: add Sprint 2 implementation documentation

- Part 01-14: Complete API key management guide
- Design, implementation, testing, deployment docs"
```

### Step 3.4: Push to Remote

```powershell
# Push and set upstream
git push -u origin feature/sprint-2-api-key-management
```

### Step 3.5: Create Pull Request

**On GitHub:**

1. Navigate to repository
2. Click "Compare & pull request"
3. Fill in PR template:

```markdown
## Summary

Sprint 2 implementation of API Key Management system.

## Changes

### Backend
- 🔐 **ApiKeyAuthFilter** - Gateway filter for X-Api-Key authentication
- 🔑 **API Key Generation** - Stripe-style keys (pk_test_, sk_test_, pk_live_, sk_live_)
- 📋 **Key Management** - List, revoke, validate endpoints
- 🪝 **Webhook Configuration** - URL and secret management
- ⚡ **Redis Caching** - 5 minute TTL for key validation

### Frontend
- 📄 **ApiKeysPage** - Complete key management UI
- 🔘 **Generate Keys** - TEST and LIVE key generation
- 📊 **Key Table** - List with status, created date, last used
- ✂️ **Revoke Function** - With confirmation dialog
- 🔗 **Webhook Config** - URL input with secret display

## Technical Details

| Feature | Implementation |
|---------|----------------|
| Key Format | `pk_test_`, `sk_test_`, `pk_live_`, `sk_live_` |
| Secret Hashing | SHA-256 |
| Cache TTL | 5 minutes |
| Key ID Prefix | `key_` |

## Testing

- [ ] API key generation works (TEST + LIVE)
- [ ] API key authentication via X-Api-Key header
- [ ] Key list shows correct data (secrets masked)
- [ ] Key revocation prevents future auth
- [ ] Webhook URL update regenerates secret
- [ ] Frontend flows work end-to-end
- [ ] Unit tests pass
- [ ] E2E tests pass

## Screenshots

### API Keys Page
[Add screenshot of ApiKeysPage]

### New Key Modal
[Add screenshot of key generation modal]

### Swagger Documentation
[Add screenshot of new endpoints in Swagger]

## Checklist

- [ ] Code follows project conventions
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests added/updated
- [ ] No console errors
- [ ] Responsive design verified
```

---

## 4. Branch Protection Rules

Ensure these rules are set for `develop` branch:

```yaml
Branch protection rules:
  - Require pull request reviews: 1
  - Require status checks to pass:
    - CI Backend
    - CI Frontend
  - Require branches to be up to date
  - Restrict who can push: Only PRs allowed
```

---

## 5. PR Review Guidelines

### For Reviewers

**Check these areas:**

1. **Security**
   - API key hashing uses SHA-256
   - Secrets not logged or exposed
   - Cache invalidation on revoke
   - Internal endpoints not exposed

2. **Code Quality**
   - Error handling complete
   - Logging appropriate
   - No hardcoded values
   - Consistent naming

3. **Testing**
   - Unit tests for service methods
   - E2E tests for full flow
   - Edge cases covered

4. **Frontend**
   - Accessibility (labels, ARIA)
   - Error states handled
   - Loading states shown
   - Copy feedback provided

---

## 6. Post-Merge Actions

After PR is merged:

```powershell
# Update local develop
git checkout develop
git pull origin develop

# Delete feature branch (optional)
git branch -d feature/sprint-2-api-key-management
git push origin --delete feature/sprint-2-api-key-management

# Tag the release
git tag -a v0.2.0 -m "Sprint 2: API Key Management"
git push origin v0.2.0
```

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Feature branch** | `feature/sprint-2-api-key-management` |
| **Atomic commits** | One concern per commit |
| **Conventional commits** | `feat(scope): description` |
| **PR template** | Summary, changes, testing, screenshots |
| **Code review** | Security, quality, testing, UX |

---

## 8. Next Steps

**Continue to:** [part-14-sprint-summary.md](./part-14-sprint-summary.md)

In the final part, you'll review everything built in Sprint 2.

---

**End of Sprint 2, Part 13**
