# Sprint 2, Part 14: Sprint Summary

**Sprint Duration:** 1-2 weeks  
**Status:** Complete ✅

---

## 1. What We Built

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SPRINT 2 DELIVERABLES                                    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    API KEY AUTHENTICATION                            │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────┐      │   │
│  │  │                   API GATEWAY                             │      │   │
│  │  │                                                           │      │   │
│  │  │  ApiKeyAuthFilter (NEW)                                  │      │   │
│  │  │  ├── Validates X-Api-Key header                          │      │   │
│  │  │  ├── SHA-256 hash for secure lookup                      │      │   │
│  │  │  ├── Redis cache (5 min TTL)                             │      │   │
│  │  │  ├── Adds X-Merchant-Id header                           │      │   │
│  │  │  └── Supports TEST + LIVE keys                           │      │   │
│  │  │                                                           │      │   │
│  │  │  Port: 8080                                              │      │   │
│  │  └──────────────────────────────────────────────────────────┘      │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    MERCHANT SERVICE ENHANCEMENTS                     │   │
│  │                                                                      │   │
│  │  New Endpoints:                                                     │   │
│  │  ──────────────                                                     │   │
│  │  POST   /v1/merchants/{id}/api-keys         → Generate key pair    │   │
│  │  GET    /v1/merchants/{id}/api-keys         → List all keys        │   │
│  │  DELETE /v1/merchants/{id}/api-keys/{keyId} → Revoke key           │   │
│  │  PUT    /v1/merchants/{id}/webhook          → Update webhook URL   │   │
│  │  GET    /v1/merchants/{id}/webhook          → Get webhook config   │   │
│  │                                                                      │   │
│  │  Internal Endpoint:                                                 │   │
│  │  ─────────────────                                                  │   │
│  │  POST   /internal/validate-api-key          → Gateway validation   │   │
│  │                                                                      │   │
│  │  Port: 8082                                                         │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    FRONTEND DASHBOARD ENHANCEMENTS                   │   │
│  │                                                                      │   │
│  │  New Page: ApiKeysPage                                              │   │
│  │  ────────────────────                                               │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │   │
│  │  │ Generate     │  │ Key Table    │  │ Webhook      │              │   │
│  │  │ Section      │  │              │  │ Config       │              │   │
│  │  │              │  │              │  │              │              │   │
│  │  │ • TEST btn   │  │ • Type badge │  │ • URL input  │              │   │
│  │  │ • LIVE btn   │  │ • Key prefix │  │ • Save btn   │              │   │
│  │  │              │  │ • Status     │  │ • Secret     │              │   │
│  │  │              │  │ • Dates      │  │   show/hide  │              │   │
│  │  │              │  │ • Revoke btn │  │ • Copy btn   │              │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │   │
│  │                                                                      │   │
│  │  New Key Modal (on generation):                                     │   │
│  │  ├── Shows public_key (pk_test_xxx)                                │   │
│  │  ├── Shows secret_key (sk_test_xxx) ⚠️ One-time display           │   │
│  │  ├── Copy buttons                                                   │   │
│  │  └── "I've saved my secret key" dismiss button                     │   │
│  │                                                                      │   │
│  │  Port: 3000                                                         │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Technical Achievements

### Features Implemented

| Feature | Implementation | Status |
|---------|----------------|--------|
| API Key Generation | Stripe-style keys with SecureRandom | ✅ |
| Key Authentication | X-Api-Key header via Gateway filter | ✅ |
| Secret Hashing | SHA-256 (keys stored hashed) | ✅ |
| Caching | Redis with 5 minute TTL | ✅ |
| Key Revocation | Soft delete with status change | ✅ |
| Webhook Config | URL + secret management | ✅ |
| Frontend UI | ApiKeysPage React component | ✅ |

### API Key Format

| Key Type | Public Key Format | Secret Key Format |
|----------|-------------------|-------------------|
| TEST | `pk_test_` + 32 chars | `sk_test_` + 32 chars |
| LIVE | `pk_live_` + 32 chars | `sk_live_` + 32 chars |

### Security Implementation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECURITY MEASURES                                         │
│                                                                              │
│  1. Secret Key Storage                                                      │
│     ├── Never store plain text secrets                                      │
│     ├── SHA-256 hash stored in database                                     │
│     └── Original secret shown ONCE at generation                            │
│                                                                              │
│  2. Key Validation                                                          │
│     ├── Hash incoming key                                                   │
│     ├── Compare hash with stored hash                                       │
│     └── No timing attacks (constant-time compare)                           │
│                                                                              │
│  3. Cache Security                                                          │
│     ├── Store: merchantId:keyType:status (NOT the key)                     │
│     ├── TTL: 5 minutes (limits exposure window)                            │
│     └── Key: hash of API key (not plain text)                              │
│                                                                              │
│  4. Webhook Security                                                        │
│     ├── Unique secret per merchant (whsec_xxx)                             │
│     ├── Secret regenerated on URL change                                   │
│     └── HMAC signature for webhook payloads                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Parts Completed

| Part | Name | Duration |
|------|------|----------|
| 01 | API Key Design | 1h |
| 02 | API Key Generation | 2h |
| 03 | API Key Authentication | 2h |
| 04 | Webhook Configuration | 1-2h |
| 05 | Fee Plans | 1-2h |
| 06 | Merchant Swagger Testing | 1h |
| 07 | Frontend ApiKeysPage | 2-3h |
| 08 | Frontend Settings Page | 1-2h |
| 09 | Docker Update | 1h |
| 10 | CI/CD & Postman | 1-2h |
| 11 | AWS Deployment | 1h |
| 12 | E2E Testing | 2-3h |
| 13 | Git & PR | 1h |
| 14 | Sprint Summary | 30m |

**Total Implementation: ~18-22 hours**

---

## 4. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SPRINT 2 ARCHITECTURE                                    │
│                                                                              │
│  External Client (API Consumer)                                             │
│       │                                                                      │
│       │ X-Api-Key: sk_test_abc123...                                        │
│       ▼                                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                         API GATEWAY (:8080)                         │    │
│  │                                                                     │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐        │    │
│  │  │ Correlation │→ │ Rate Limit  │→ │ ApiKeyAuthFilter    │        │    │
│  │  │ IdFilter    │  │ Filter      │  │ (NEW in Sprint 2)   │        │    │
│  │  └─────────────┘  └─────────────┘  └──────────┬──────────┘        │    │
│  │                                               │                     │    │
│  │                    ┌──────────────────────────┴──────────────┐     │    │
│  │                    │                                          │     │    │
│  │                    ▼                                          │     │    │
│  │           ┌─────────────────┐                                │     │    │
│  │           │   Redis Cache   │  ← Check cache first           │     │    │
│  │           │   apikey:hash   │    (5 min TTL)                 │     │    │
│  │           └────────┬────────┘                                │     │    │
│  │                    │ Cache MISS                               │     │    │
│  │                    ▼                                          │     │    │
│  │           POST /internal/validate-api-key                    │     │    │
│  │                                                               │     │    │
│  └─────────────────────────────┬─────────────────────────────────┘    │
│                                │                                       │
│       ┌────────────────────────┼────────────────────────┐             │
│       │                        │                        │             │
│       ▼                        ▼                        ▼             │
│  ┌──────────┐           ┌──────────────┐         ┌──────────┐        │
│  │ Identity │           │   Merchant   │         │ Payment  │        │
│  │ Service  │           │   Service    │         │ Service  │        │
│  │ :8081    │           │   :8082      │         │ :8083    │        │
│  └──────────┘           └──────┬───────┘         └──────────┘        │
│                                │                                      │
│                                ▼                                      │
│                    ┌──────────────────────┐                          │
│                    │     PostgreSQL       │                          │
│                    │   api_keys table     │                          │
│                    │   (hash stored)      │                          │
│                    └──────────────────────┘                          │
│                                                                       │
│  Browser (Merchant Dashboard)                                        │
│       │                                                               │
│       ▼                                                               │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │              FRONTEND DASHBOARD (:3000)                   │       │
│  │                                                           │       │
│  │  /dashboard → "Manage API Keys" button                   │       │
│  │       │                                                   │       │
│  │       ▼                                                   │       │
│  │  /api-keys → ApiKeysPage                                 │       │
│  │       ├── Generate TEST/LIVE keys                        │       │
│  │       ├── List keys (secrets masked)                     │       │
│  │       ├── Revoke keys                                    │       │
│  │       └── Configure webhook                              │       │
│  │                                                           │       │
│  └──────────────────────────────────────────────────────────┘       │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 5. Key Learnings

### Technical Skills

| Category | Skills Learned |
|----------|----------------|
| Backend | Spring Cloud Gateway filters, Reactive WebClient |
| Security | API key hashing, Redis caching, webhook secrets |
| Database | JPA custom queries, soft deletes |
| Frontend | React state management, clipboard API, modals |
| Testing | E2E with REST Assured, Playwright |

### Patterns Applied

| Pattern | Usage |
|---------|-------|
| Gateway Filter | `GlobalFilter` for authentication |
| Cache Aside | Redis cache with database fallback |
| DTO Records | Java records for request/response |
| Soft Delete | Status field instead of DELETE |
| One-Time Secret | Show secret only at creation |
| Masked Display | Show prefix only in lists |

---

## 6. Verified Configuration Values

| Item | Value |
|------|-------|
| Spring Boot | `3.2.5` |
| API Key Prefix (Test Public) | `pk_test_` |
| API Key Prefix (Test Secret) | `sk_test_` |
| API Key Prefix (Live Public) | `pk_live_` |
| API Key Prefix (Live Secret) | `sk_live_` |
| Key ID Prefix | `key_` |
| Webhook Secret Prefix | `whsec_` |
| Secret Key Length | 40 chars (prefix + 32 random) |
| Hashing Algorithm | SHA-256 |
| Cache TTL | 5 minutes |
| Cache Key Format | `apikey:{sha256_hash}` |
| Cache Value Format | `merchantId:keyType:status` |
| Token Storage Key | `payflow_token` |
| Entity Folder | `model` |
| ID Type | `String` / `VARCHAR(50)` |
| Timestamp Type | `Instant` |

---

## 7. Verification Checklist

Before moving to Sprint 3, verify:

### Backend Services Running
```powershell
# Health checks
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Identity
curl http://localhost:8082/actuator/health  # Merchant
```

### API Key Generation
```powershell
# Generate TEST key (requires JWT auth)
curl -X POST "http://localhost:8080/v1/merchants/{merchantId}/api-keys?keyType=TEST" \
  -H "Authorization: Bearer {jwt_token}"

# Expected response:
# {
#   "success": true,
#   "data": {
#     "key_id": "key_abc123...",
#     "key_type": "TEST",
#     "public_key": "pk_test_...",
#     "secret_key": "sk_test_...",
#     "note": "Save the secret_key now..."
#   }
# }
```

### API Key Authentication
```powershell
# Authenticate with API key
curl http://localhost:8080/v1/merchants/{merchantId} \
  -H "X-Api-Key: sk_test_your_secret_key"

# Expected: 200 OK with merchant data
```

### Frontend
- [ ] Navigate to http://localhost:3000
- [ ] Login with existing user
- [ ] Dashboard shows "Manage API Keys" button
- [ ] Click → navigates to /api-keys
- [ ] Generate TEST key works
- [ ] Key modal shows public + secret
- [ ] Copy buttons work
- [ ] Key appears in table
- [ ] Revoke button works
- [ ] Webhook URL can be saved

### CI/CD
- [ ] GitHub Actions workflow passes
- [ ] Docker images build successfully
- [ ] Postman collection tests pass

---

## 8. Database Changes

### api_keys Table

```sql
CREATE TABLE api_keys (
    id VARCHAR(50) PRIMARY KEY,           -- key_xxx
    merchant_id VARCHAR(50) NOT NULL,     -- mrc_xxx
    key_type VARCHAR(10) NOT NULL,        -- TEST or LIVE
    public_key VARCHAR(50) NOT NULL,      -- pk_test_xxx or pk_live_xxx
    secret_key_hash VARCHAR(64) NOT NULL, -- SHA-256 hash
    key_prefix VARCHAR(20) NOT NULL,      -- First 12 chars for display
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE or REVOKED
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    
    FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_api_keys_merchant ON api_keys(merchant_id);
CREATE INDEX idx_api_keys_hash ON api_keys(secret_key_hash);
```

---

## 9. Sprint 3 Preview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SPRINT 3: PAYMENT PROCESSING                             │
│                                                                              │
│  What's Next:                                                               │
│                                                                              │
│  • Payment Service (order creation, payment processing)                     │
│  • ISO 8583 protocol implementation                                         │
│  • Bank Simulator for testing                                               │
│  • Transaction state machine (created → authorized → captured)             │
│  • Idempotency handling with Redis                                         │
│  • Payment methods (Card, UPI, NetBanking)                                  │
│  • Hosted Checkout page                                                     │
│  • Webhook delivery for payment events                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Congratulations! 🎉

You've completed Sprint 2 of the PayFlow Payment Gateway!

**What you built:**
- ✅ API Key generation (Stripe-style TEST + LIVE keys)
- ✅ API Key authentication via Gateway filter
- ✅ Secure key storage with SHA-256 hashing
- ✅ Redis caching for fast validation
- ✅ Key management (list, revoke)
- ✅ Webhook configuration
- ✅ React ApiKeysPage with full CRUD
- ✅ E2E tests for key lifecycle
- ✅ Updated CI/CD pipeline

**Next:** [Sprint 3 - Payment Processing](../../sprint-03-payment-processing/README.md)

---

**End of Sprint 2**

*Total implementation parts: 14*  
*Ready for Sprint 3!*
