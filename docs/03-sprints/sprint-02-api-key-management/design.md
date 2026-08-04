# Sprint 2: API Key Management — Technical Design

**Sprint Duration:** 2 weeks  
**Goal:** Merchants can generate API keys and configure webhook/notification settings

---

## 1. Architecture Overview

### 1.1 Sprint 2 Additions

| Component | Type | Purpose |
|-----------|------|---------|
| API Key Service | New Logic | API key generation, validation |
| API Key Gateway Filter | New Filter | Authenticate requests via API key |
| Webhook Config | New Feature | Store webhook settings |
| Merchant Settings | New Feature | Account preferences |
| Redis Cache | Enhancement | Cache API keys for fast validation |

### 1.2 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SPRINT 2 ARCHITECTURE                               │
│                                                                              │
│  ┌─────────────┐     API Key: pk_live_xxx...                                │
│  │ Merchant's  │ ──────────────────────────────────────┐                    │
│  │   Server    │                                       │                    │
│  └─────────────┘                                       │                    │
│                                                        ▼                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        API Gateway (:8080)                           │   │
│  │                                                                      │   │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐    │   │
│  │  │ API Key Filter   │  │ JWT Auth Filter  │  │ Rate Limiter   │    │   │
│  │  │ (NEW in Sprint 2)│  │ (from Sprint 1)  │  │ (per API key)  │    │   │
│  │  └────────┬─────────┘  └──────────────────┘  └────────────────┘    │   │
│  │           │                                                         │   │
│  │           │ Validate key hash                                       │   │
│  │           ▼                                                         │   │
│  │  ┌──────────────────┐                                              │   │
│  │  │  Redis Cache     │ ← Cached API key metadata                    │   │
│  │  │  (fast lookup)   │   TTL: 5 minutes                             │   │
│  │  └────────┬─────────┘                                              │   │
│  │           │ Cache miss?                                             │   │
│  │           ▼                                                         │   │
│  └───────────┼─────────────────────────────────────────────────────────┘   │
│              │                                                              │
│              ▼                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Merchant Service (:8082)                         │   │
│  │                                                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐          │   │
│  │  │ API Key      │  │  Webhook     │  │ Settings         │          │   │
│  │  │ Management   │  │  Config      │  │ Management       │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     PostgreSQL (:5432)                               │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐          │   │
│  │  │  api_keys    │  │  merchant_   │  │ merchant_        │          │   │
│  │  │  table       │  │  webhooks    │  │ settings         │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 2. API Key Design

### 2.1 API Key Format

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API KEY FORMAT                                       │
│                                                                              │
│  Full Key (shown once to user):                                              │
│  pk_live_EXAMPLE_RANDOM_STRING_HERE                                         │
│  │   │    │                                                                  │
│  │   │    └── 52 character random string (Base62 encoded)                   │
│  │   │                                                                       │
│  │   └─────── Environment: live (production) or test (sandbox)              │
│  │                                                                           │
│  └─────────── Prefix: pk = PayFlow Key                                      │
│                                                                              │
│  Key ID (visible, stored):                                                   │
│  pk_live_a1b2c3d4  ← First 8 chars used as identifier                       │
│                                                                              │
│  Key Hash (stored in DB):                                                    │
│  SHA-256(full_key) = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 │
│                                                                              │
│  Security Properties:                                                        │
│  • 52 chars × 6 bits/char = 312 bits of entropy                             │
│  • Only hash stored (not reversible)                                         │
│  • Prefix allows visual identification                                       │
│  • Key ID allows listing without exposing full key                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 API Key Generation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API KEY GENERATION FLOW                                  │
│                                                                              │
│  Merchant Dashboard                    Backend                               │
│        │                                  │                                  │
│        │ Click "Create API Key"           │                                  │
│        │─────────────────────────────────►│                                  │
│        │                                  │                                  │
│        │                           ┌──────┴──────┐                          │
│        │                           │ Generate    │                          │
│        │                           │ 52-char     │                          │
│        │                           │ random key  │                          │
│        │                           └──────┬──────┘                          │
│        │                                  │                                  │
│        │                           ┌──────┴──────┐                          │
│        │                           │ Hash key    │                          │
│        │                           │ with SHA-256│                          │
│        │                           └──────┬──────┘                          │
│        │                                  │                                  │
│        │                           ┌──────┴──────┐                          │
│        │                           │ Store:      │                          │
│        │                           │ • key_id    │                          │
│        │                           │ • key_hash  │                          │
│        │                           │ • metadata  │                          │
│        │                           └──────┬──────┘                          │
│        │                                  │                                  │
│        │◄─────────────────────────────────│                                  │
│        │ Return: Full key (ONCE!)         │                                  │
│        │                                  │                                  │
│  ┌─────┴─────┐                            │                                  │
│  │ Show to   │  "Your API Key:            │                                  │
│  │ user      │   pk_live_a1b2c3d4..."     │                                  │
│  │           │                            │                                  │
│  │ ⚠️ Copy   │  "This won't be shown      │                                  │
│  │ now!      │   again!"                  │                                  │
│  └───────────┘                            │                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.3 API Key Validation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API KEY VALIDATION FLOW                                  │
│                                                                              │
│  Merchant Server                API Gateway                  Redis/DB       │
│        │                            │                            │          │
│        │ POST /v1/payments          │                            │          │
│        │ X-Api-Key: pk_live_xxx...  │                            │          │
│        │───────────────────────────►│                            │          │
│        │                            │                            │          │
│        │                     ┌──────┴──────┐                     │          │
│        │                     │ Extract key │                     │          │
│        │                     │ from header │                     │          │
│        │                     └──────┬──────┘                     │          │
│        │                            │                            │          │
│        │                     ┌──────┴──────┐                     │          │
│        │                     │ Hash the key│                     │          │
│        │                     │ SHA-256(key)│                     │          │
│        │                     └──────┬──────┘                     │          │
│        │                            │                            │          │
│        │                            │ GET cache:apikey:{hash}    │          │
│        │                            │───────────────────────────►│          │
│        │                            │                            │          │
│        │                            │◄───────────────────────────│          │
│        │                            │ Cache HIT: Return metadata │          │
│        │                            │ Cache MISS: Query DB       │          │
│        │                            │                            │          │
│        │                     ┌──────┴──────┐                     │          │
│        │                     │ Validate:   │                     │          │
│        │                     │ • is_active │                     │          │
│        │                     │ • not_expired│                    │          │
│        │                     │ • permissions│                    │          │
│        │                     └──────┬──────┘                     │          │
│        │                            │                            │          │
│        │                     ┌──────┴──────┐                     │          │
│        │                     │ Add headers:│                     │          │
│        │                     │ X-Merchant-Id│                    │          │
│        │                     │ X-Key-Id    │                     │          │
│        │                     └──────┬──────┘                     │          │
│        │                            │                            │          │
│        │                            │ Forward to service         │          │
│        │                            ▼                            │          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 Why Cache API Keys?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CACHING STRATEGY                                         │
│                                                                              │
│  Without Cache:                      With Redis Cache:                       │
│  ───────────────                     ─────────────────                       │
│                                                                              │
│  Every API Request:                  First Request:                          │
│  ┌────────────────┐                  ┌────────────────┐                     │
│  │ Query DB       │                  │ Query DB       │                     │
│  │ SELECT * FROM  │  ~5-10ms         │ SELECT * FROM  │  ~5-10ms            │
│  │ api_keys       │                  │ api_keys       │                     │
│  │ WHERE hash=... │                  │ WHERE hash=... │                     │
│  └────────────────┘                  └───────┬────────┘                     │
│                                              │                               │
│                                              ▼                               │
│                                      ┌────────────────┐                     │
│                                      │ Store in Redis │                     │
│                                      │ TTL: 5 minutes │                     │
│                                      └────────────────┘                     │
│                                                                              │
│                                      Subsequent Requests:                    │
│                                      ┌────────────────┐                     │
│                                      │ GET from Redis │  ~0.5ms             │
│                                      │ (in-memory)    │                     │
│                                      └────────────────┘                     │
│                                                                              │
│  1000 requests/sec = 1000 DB queries     1000 requests/sec = 1 DB query     │
│  DB under heavy load                      + 999 Redis lookups               │
│                                           DB stays fast                      │
│                                                                              │
│  Cache Invalidation:                                                         │
│  • Key revoked → Delete from cache immediately                              │
│  • TTL ensures stale data expires within 5 minutes                          │
│  • Revocation takes effect within TTL window                                │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 3. Gateway Filter Design

### 3.1 Filter Chain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY FILTER CHAIN                                 │
│                                                                              │
│  Incoming Request                                                            │
│        │                                                                     │
│        ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 1. Correlation ID Filter (from Sprint 1)                            │   │
│  │    • Generates X-Correlation-ID if not present                      │   │
│  │    • Passes through if present                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 2. Rate Limit Filter (enhanced in Sprint 2)                         │   │
│  │    • Check rate limit by API key (if present)                       │   │
│  │    • Fall back to IP-based limit                                    │   │
│  │    • Returns 429 if exceeded                                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 3. API Key Authentication Filter (NEW in Sprint 2)                  │   │
│  │    • Check for X-Api-Key header                                     │   │
│  │    • Validate key, extract merchant context                         │   │
│  │    • If valid: Add X-Merchant-Id header, continue                   │   │
│  │    • If no key: Fall through to JWT filter                          │   │
│  │    • If invalid key: Return 401                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 4. JWT Authentication Filter (from Sprint 1)                        │   │
│  │    • Check for Authorization: Bearer header                         │   │
│  │    • Validate JWT token                                             │   │
│  │    • If valid: Add X-User-Id header, continue                       │   │
│  │    • If invalid/missing: Return 401 (for protected routes)          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│        │                                                                     │
│        ▼                                                                     │
│  Route to appropriate service                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Authentication Priority

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  AUTHENTICATION METHOD PRIORITY                              │
│                                                                              │
│  Request comes in with:                                                      │
│                                                                              │
│  CASE 1: X-Api-Key header present                                           │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ ✓ Use API Key authentication                                         │  │
│  │ • Hash the key, validate against database/cache                      │  │
│  │ • If valid → Extract merchant_id, set X-Merchant-Id header          │  │
│  │ • If invalid → Return 401 (don't fall back to JWT)                  │  │
│  │ • JWT header is IGNORED if API key is present                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  CASE 2: Authorization: Bearer header (JWT) present, no X-Api-Key           │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ ✓ Use JWT authentication (Sprint 1 behavior)                         │  │
│  │ • Validate JWT signature and expiration                              │  │
│  │ • If valid → Extract user_id, set X-User-Id header                  │  │
│  │ • If invalid → Return 401                                           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  CASE 3: Neither header present                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ ? Check if route is public or protected                              │  │
│  │ • Public routes (/auth/*, /health) → Allow through                  │  │
│  │ • Protected routes → Return 401 Unauthorized                        │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  Why this priority?                                                          │
│  • API keys are for server-to-server (machine) communication               │
│  • JWTs are for user-to-server (human) communication                       │
│  • Server integrations should use API keys (simpler, no expiry)            │
│  • Dashboard users should use JWT (short-lived, more secure)               │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 4. Database Design

### 4.1 New Tables

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SPRINT 2 DATABASE SCHEMA                            │
│                                                                              │
│  ┌───────────────────────────────────────────────┐                          │
│  │                 api_keys                       │                          │
│  │  (merchant schema)                             │                          │
│  ├───────────────────────────────────────────────┤                          │
│  │ PK │ id            │ UUID                     │                          │
│  │ FK │ merchant_id   │ VARCHAR(50)              │ → merchants.id           │
│  │    │ key_id        │ VARCHAR(20) UNIQUE       │ pk_live_a1b2c3d4         │
│  │    │ key_hash      │ VARCHAR(64)              │ SHA-256 hash             │
│  │    │ name          │ VARCHAR(100)             │ "Production Key"         │
│  │    │ permissions   │ VARCHAR(20)              │ READ, WRITE, FULL        │
│  │    │ environment   │ VARCHAR(10)              │ live, test               │
│  │    │ last_used_at  │ TIMESTAMP                │ Usage tracking           │
│  │    │ expires_at    │ TIMESTAMP NULL           │ Optional expiry          │
│  │    │ is_active     │ BOOLEAN                  │ For soft revocation      │
│  │    │ created_at    │ TIMESTAMP                │                          │
│  │    │ revoked_at    │ TIMESTAMP NULL           │ When revoked             │
│  │    │ revoked_by    │ UUID NULL                │ Who revoked              │
│  └───────────────────────────────────────────────┘                          │
│                                                                              │
│  ┌───────────────────────────────────────────────┐                          │
│  │              merchant_webhooks                 │                          │
│  │  (merchant schema)                             │                          │
│  ├───────────────────────────────────────────────┤                          │
│  │ PK │ id            │ UUID                     │                          │
│  │ FK │ merchant_id   │ VARCHAR(50) UNIQUE       │ → merchants.id           │
│  │    │ url           │ VARCHAR(500)             │ https://example.com/hook │
│  │    │ secret        │ VARCHAR(64)              │ HMAC signing key         │
│  │    │ events        │ TEXT[]                   │ [payment.success, ...]   │
│  │    │ is_active     │ BOOLEAN                  │                          │
│  │    │ created_at    │ TIMESTAMP                │                          │
│  │    │ updated_at    │ TIMESTAMP                │                          │
│  └───────────────────────────────────────────────┘                          │
│                                                                              │
│  ┌───────────────────────────────────────────────┐                          │
│  │              merchant_settings                 │                          │
│  │  (merchant schema)                             │                          │
│  ├───────────────────────────────────────────────┤                          │
│  │ PK │ id            │ UUID                     │                          │
│  │ FK │ merchant_id   │ VARCHAR(50) UNIQUE       │ → merchants.id           │
│  │    │ default_currency│ VARCHAR(3)             │ INR, USD, etc.           │
│  │    │ auto_capture  │ BOOLEAN                  │ Auto-capture payments    │
│  │    │ test_mode     │ BOOLEAN                  │ Sandbox mode             │
│  │    │ settlement_schedule│ VARCHAR(20)         │ DAILY, WEEKLY            │
│  │    │ notification_email│ VARCHAR(255)         │ Alerts email             │
│  │    │ created_at    │ TIMESTAMP                │                          │
│  │    │ updated_at    │ TIMESTAMP                │                          │
│  └───────────────────────────────────────────────┘                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Entity Relationships

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ENTITY RELATIONSHIPS                                    │
│                                                                              │
│                 ┌─────────────────┐                                         │
│                 │    merchants    │                                         │
│                 │  (from Sprint 1)│                                         │
│                 └────────┬────────┘                                         │
│                          │                                                   │
│           ┌──────────────┼──────────────┬──────────────┐                    │
│           │              │              │              │                    │
│           │ 1:N          │ 1:1          │ 1:1          │                    │
│           ▼              ▼              ▼              ▼                    │
│     ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐            │
│     │ api_keys │  │ merchant_    │  │ merchant_│  │ (future) │            │
│     │          │  │ webhooks     │  │ settings │  │ tables   │            │
│     │ Up to 5  │  │              │  │          │  │          │            │
│     │ per      │  │ One config   │  │ One      │  │          │            │
│     │ merchant │  │ per merchant │  │ per      │  │          │            │
│     └──────────┘  └──────────────┘  │ merchant │  │          │            │
│                                     └──────────┘  └──────────┘            │
│                                                                              │
│  Cascade Rules:                                                              │
│  • Merchant deleted → All related records cascade delete                    │
│  • API key revoked → Soft delete (is_active = false)                       │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 4.3 SQL Schema

```sql
-- API Keys table
CREATE TABLE merchant.api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id) ON DELETE CASCADE,
    key_id VARCHAR(20) NOT NULL UNIQUE,
    key_hash VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    permissions VARCHAR(20) NOT NULL DEFAULT 'FULL',
    environment VARCHAR(10) NOT NULL DEFAULT 'live',
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_by UUID,
    
    CONSTRAINT chk_permissions CHECK (permissions IN ('READ', 'WRITE', 'FULL')),
    CONSTRAINT chk_environment CHECK (environment IN ('live', 'test'))
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(key_hash);
CREATE INDEX idx_api_keys_active ON merchant.api_keys(is_active) WHERE is_active = true;

-- Webhook configuration table
CREATE TABLE merchant.merchant_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(50) NOT NULL UNIQUE REFERENCES merchant.merchants(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    secret VARCHAR(64) NOT NULL,
    events TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Merchant settings table
CREATE TABLE merchant.merchant_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(50) NOT NULL UNIQUE REFERENCES merchant.merchants(id) ON DELETE CASCADE,
    default_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    auto_capture BOOLEAN NOT NULL DEFAULT true,
    test_mode BOOLEAN NOT NULL DEFAULT false,
    settlement_schedule VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    notification_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_settlement CHECK (settlement_schedule IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);
```

---

## 5. Class Design

### 5.1 API Key Service Classes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY SERVICE CLASS STRUCTURE                           │
│                                                                              │
│  merchant-service/                                                           │
│  │                                                                           │
│  ├── entity/                                                                 │
│  │   ├── ApiKey.java                 ← JPA entity                           │
│  │   ├── MerchantWebhook.java        ← JPA entity                           │
│  │   └── MerchantSettings.java       ← JPA entity                           │
│  │                                                                           │
│  ├── repository/                                                             │
│  │   ├── ApiKeyRepository.java       ← JPA repository                       │
│  │   │   • findByKeyHash(hash)                                              │
│  │   │   • findByMerchantIdAndIsActive(merchantId, true)                    │
│  │   │   • countByMerchantIdAndIsActive(merchantId, true)                   │
│  │   ├── WebhookRepository.java                                             │
│  │   └── SettingsRepository.java                                            │
│  │                                                                           │
│  ├── service/                                                                │
│  │   ├── ApiKeyService.java          ← Business logic interface             │
│  │   │   • generateApiKey(merchantId, name, permissions)                    │
│  │   │   • validateApiKey(keyHash)                                          │
│  │   │   • revokeApiKey(keyId)                                              │
│  │   │   • listApiKeys(merchantId)                                          │
│  │   │   • rotateApiKey(keyId)                                              │
│  │   │                                                                       │
│  │   ├── WebhookService.java                                                │
│  │   │   • configureWebhook(merchantId, url, events)                        │
│  │   │   • getWebhookConfig(merchantId)                                     │
│  │   │   • testWebhook(merchantId)                                          │
│  │   │                                                                       │
│  │   └── SettingsService.java                                               │
│  │       • getSettings(merchantId)                                          │
│  │       • updateSettings(merchantId, settings)                             │
│  │                                                                           │
│  ├── controller/                                                             │
│  │   ├── ApiKeyController.java       ← REST endpoints                       │
│  │   │   • POST /v1/merchants/{id}/api-keys                                 │
│  │   │   • GET /v1/merchants/{id}/api-keys                                  │
│  │   │   • DELETE /v1/merchants/{id}/api-keys/{keyId}                       │
│  │   │   • POST /v1/merchants/{id}/api-keys/{keyId}/rotate                  │
│  │   │                                                                       │
│  │   ├── WebhookController.java                                             │
│  │   │   • PUT /v1/merchants/{id}/webhook                                   │
│  │   │   • GET /v1/merchants/{id}/webhook                                   │
│  │   │   • POST /v1/merchants/{id}/webhook/test                             │
│  │   │                                                                       │
│  │   └── SettingsController.java                                            │
│  │       • GET /v1/merchants/{id}/settings                                  │
│  │       • PUT /v1/merchants/{id}/settings                                  │
│  │                                                                           │
│  ├── dto/                                                                    │
│  │   ├── CreateApiKeyRequest.java                                           │
│  │   ├── ApiKeyResponse.java         ← Does NOT include full key            │
│  │   ├── NewApiKeyResponse.java      ← Includes full key (once!)            │
│  │   ├── WebhookConfigRequest.java                                          │
│  │   ├── WebhookResponse.java                                               │
│  │   ├── SettingsRequest.java                                               │
│  │   └── SettingsResponse.java                                              │
│  │                                                                           │
│  └── util/                                                                   │
│      ├── ApiKeyGenerator.java        ← Secure key generation                │
│      │   • generateKey(environment)  → "pk_live_xxx..."                     │
│      │   • extractKeyId(fullKey)     → "pk_live_a1b2"                       │
│      │   • hashKey(fullKey)          → SHA-256 hash                         │
│      └── WebhookSecretGenerator.java                                        │
│          • generateSecret()          → 32-byte hex string                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 5.2 Gateway Filter Classes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY FILTER STRUCTURE                              │
│                                                                              │
│  api-gateway/                                                                │
│  │                                                                           │
│  ├── filter/                                                                 │
│  │   ├── ApiKeyAuthenticationFilter.java  ← NEW in Sprint 2                 │
│  │   │   @Component                                                         │
│  │   │   @Order(2)  // After rate limit, before JWT                         │
│  │   │                                                                       │
│  │   │   Methods:                                                           │
│  │   │   • filter(exchange, chain)                                          │
│  │   │     - Extract X-Api-Key header                                       │
│  │   │     - If present: validate key, add merchant context                 │
│  │   │     - If absent: pass through to JWT filter                          │
│  │   │                                                                       │
│  │   │   Dependencies:                                                       │
│  │   │   • ApiKeyValidationService                                          │
│  │   │   • RedisTemplate (for caching)                                      │
│  │   │                                                                       │
│  │   ├── JwtAuthenticationFilter.java     (existing from Sprint 1)          │
│  │   │   @Order(3)                                                          │
│  │   │                                                                       │
│  │   ├── RateLimitFilter.java             (enhanced in Sprint 2)            │
│  │   │   @Order(1)                                                          │
│  │   │   • Now supports per-API-key rate limits                             │
│  │   │                                                                       │
│  │   └── CorrelationIdFilter.java         (existing from Sprint 1)          │
│  │       @Order(0)                                                          │
│  │                                                                           │
│  ├── service/                                                                │
│  │   └── ApiKeyValidationService.java     ← NEW in Sprint 2                 │
│  │       • validateKey(apiKey) → ApiKeyInfo                                 │
│  │       • Uses Redis cache first, then calls merchant-service              │
│  │                                                                           │
│  ├── dto/                                                                    │
│  │   └── ApiKeyInfo.java                  ← Cache model                     │
│  │       • merchantId                                                       │
│  │       • keyId                                                            │
│  │       • permissions                                                      │
│  │       • environment                                                      │
│  │                                                                           │
│  └── config/                                                                 │
│      └── RedisConfig.java                 (may need updates)                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. API Endpoints Design

### 6.1 API Key Endpoints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API KEY ENDPOINTS                                        │
│                                                                              │
│  POST /v1/merchants/{merchantId}/api-keys                                   │
│  ─────────────────────────────────────────                                  │
│  Description: Generate a new API key                                         │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Request:                                                                    │
│  {                                                                           │
│    "name": "Production Server",                                             │
│    "permissions": "FULL",        // READ, WRITE, FULL                       │
│    "environment": "live",        // live, test                              │
│    "expiresAt": null             // Optional: ISO date string               │
│  }                                                                           │
│                                                                              │
│  Response (201 Created):                                                     │
│  {                                                                           │
│    "keyId": "pk_live_a1b2c3d4",                                             │
│    "key": "pk_live_a1b2c3d4e5f6g7h8...",  ← SHOWN ONLY ONCE!               │
│    "name": "Production Server",                                             │
│    "permissions": "FULL",                                                   │
│    "environment": "live",                                                   │
│    "createdAt": "2026-08-04T10:00:00Z"                                      │
│  }                                                                           │
│  ┌────────────────────────────────────────────────────────────────────────┐│
│  │ ⚠️ WARNING: The full key is shown ONLY in this response!              ││
│  │    Store it securely. It cannot be retrieved later.                   ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  GET /v1/merchants/{merchantId}/api-keys                                    │
│  ───────────────────────────────────────                                    │
│  Description: List all API keys (without showing full key)                   │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "keys": [                                                                 │
│      {                                                                       │
│        "keyId": "pk_live_a1b2c3d4",      ← Partial, safe to show           │
│        "name": "Production Server",                                         │
│        "permissions": "FULL",                                               │
│        "environment": "live",                                               │
│        "lastUsedAt": "2026-08-04T15:30:00Z",                                │
│        "createdAt": "2026-08-01T10:00:00Z",                                 │
│        "isActive": true                                                     │
│      }                                                                       │
│    ],                                                                        │
│    "total": 1,                                                              │
│    "limit": 5                            ← Max allowed                      │
│  }                                                                           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  DELETE /v1/merchants/{merchantId}/api-keys/{keyId}                         │
│  ──────────────────────────────────────────────────                         │
│  Description: Revoke an API key (soft delete)                                │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Response (204 No Content)                                                   │
│                                                                              │
│  Side Effects:                                                               │
│  • Key marked as inactive in database                                       │
│  • Cache entry invalidated immediately                                      │
│  • Any future requests with this key return 401                             │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  POST /v1/merchants/{merchantId}/api-keys/{keyId}/rotate                    │
│  ───────────────────────────────────────────────────────                    │
│  Description: Rotate an API key (generate new, revoke old)                   │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "keyId": "pk_live_x9y8z7w6",          ← NEW key ID                       │
│    "key": "pk_live_x9y8z7w6v5u4t3...",   ← NEW full key (show once!)       │
│    "name": "Production Server",           ← Same name as old                │
│    "permissions": "FULL",                                                   │
│    "oldKeyId": "pk_live_a1b2c3d4",       ← Old key (now revoked)           │
│    "createdAt": "2026-08-04T16:00:00Z"                                      │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 6.2 Webhook Endpoints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     WEBHOOK ENDPOINTS                                        │
│                                                                              │
│  PUT /v1/merchants/{merchantId}/webhook                                     │
│  ──────────────────────────────────────                                     │
│  Description: Configure webhook URL and events                               │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Request:                                                                    │
│  {                                                                           │
│    "url": "https://example.com/webhooks/payflow",                           │
│    "events": [                                                              │
│      "payment.authorized",                                                  │
│      "payment.captured",                                                    │
│      "payment.failed",                                                      │
│      "refund.created"                                                       │
│    ]                                                                        │
│  }                                                                           │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "url": "https://example.com/webhooks/payflow",                           │
│    "secret": "whsec_a1b2c3d4e5f6...",    ← For signature verification      │
│    "events": ["payment.authorized", ...],                                   │
│    "isActive": true,                                                        │
│    "createdAt": "2026-08-04T10:00:00Z"                                      │
│  }                                                                           │
│                                                                              │
│  Validation Rules:                                                           │
│  • URL must be HTTPS                                                        │
│  • URL must respond to HEAD request (accessibility check)                   │
│  • events must be valid event types                                         │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  GET /v1/merchants/{merchantId}/webhook                                     │
│  ─────────────────────────────────────                                      │
│  Description: Get current webhook configuration                              │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "url": "https://example.com/webhooks/payflow",                           │
│    "secret": "whsec_a1b2c3d4e5f6...",                                       │
│    "events": ["payment.authorized", ...],                                   │
│    "isActive": true,                                                        │
│    "lastDeliveryAt": "2026-08-04T15:30:00Z",                                │
│    "lastDeliveryStatus": "SUCCESS"                                          │
│  }                                                                           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  POST /v1/merchants/{merchantId}/webhook/test                               │
│  ────────────────────────────────────────────                               │
│  Description: Send a test webhook event                                      │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Request: (empty body)                                                       │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "success": true,                                                         │
│    "statusCode": 200,                                                       │
│    "responseTime": 150,          ← milliseconds                             │
│    "message": "Test webhook delivered successfully"                         │
│  }                                                                           │
│                                                                              │
│  Test Event Payload (sent to merchant URL):                                  │
│  {                                                                           │
│    "event": "test.webhook",                                                 │
│    "timestamp": "2026-08-04T10:00:00Z",                                     │
│    "data": {                                                                │
│      "message": "This is a test webhook from PayFlow"                       │
│    }                                                                        │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Settings Endpoints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SETTINGS ENDPOINTS                                       │
│                                                                              │
│  GET /v1/merchants/{merchantId}/settings                                    │
│  ───────────────────────────────────────                                    │
│  Description: Get merchant account settings                                  │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Response (200 OK):                                                          │
│  {                                                                           │
│    "defaultCurrency": "INR",                                                │
│    "autoCapture": true,                                                     │
│    "testMode": false,                                                       │
│    "settlementSchedule": "DAILY",                                           │
│    "notificationEmail": "finance@example.com"                               │
│  }                                                                           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  PUT /v1/merchants/{merchantId}/settings                                    │
│  ───────────────────────────────────────                                    │
│  Description: Update merchant account settings                               │
│  Auth: JWT (merchant owner only)                                            │
│                                                                              │
│  Request:                                                                    │
│  {                                                                           │
│    "defaultCurrency": "USD",                                                │
│    "autoCapture": false,                                                    │
│    "testMode": true,                                                        │
│    "settlementSchedule": "WEEKLY",                                          │
│    "notificationEmail": "alerts@example.com"                                │
│  }                                                                           │
│                                                                              │
│  Response (200 OK): Same as GET response with updated values                 │
│                                                                              │
│  Field Validation:                                                           │
│  • defaultCurrency: Must be valid ISO 4217 code (INR, USD, EUR, etc.)      │
│  • settlementSchedule: DAILY, WEEKLY, or MONTHLY                            │
│  • notificationEmail: Valid email format                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

