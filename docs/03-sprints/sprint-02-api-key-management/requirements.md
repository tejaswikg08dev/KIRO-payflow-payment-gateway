# Sprint 2: API Key Management — Requirements

**Sprint Duration:** 2 weeks  
**Goal:** Merchants can generate API keys and configure webhook/notification settings

---

## Executive Summary

This sprint enables merchants to generate and manage API keys for programmatic access to PayFlow APIs. We'll also implement webhook configuration and merchant settings management.

---

## User Stories

### US-2.1: API Key Generation

**As a** merchant  
**I want to** generate API keys for my account  
**So that** I can integrate PayFlow APIs into my application

**Acceptance Criteria:**
- [ ] Merchant can generate new API keys from the dashboard
- [ ] Each API key has a unique identifier (key_id)
- [ ] API key is shown only once upon creation (security)
- [ ] Merchant can generate up to 5 active API keys
- [ ] API key has configurable permissions (read, write, full)

---

### US-2.2: API Key Revocation

**As a** merchant  
**I want to** revoke compromised API keys  
**So that** I can maintain security of my account

**Acceptance Criteria:**
- [ ] Merchant can view all active API keys
- [ ] Merchant can revoke any API key immediately
- [ ] Revoked keys return 401 Unauthorized
- [ ] Revocation is logged in audit trail

---

### US-2.3: API Key Authentication

**As a** developer integrating PayFlow  
**I want to** authenticate API requests using API key  
**So that** I don't need to manage JWT tokens

**Acceptance Criteria:**
- [ ] API key can be passed in `X-Api-Key` header
- [ ] Valid API key grants access to merchant's resources
- [ ] Rate limits apply per API key (not per merchant)
- [ ] Invalid/revoked keys return 401 Unauthorized

---

### US-2.4: Webhook Configuration

**As a** merchant  
**I want to** configure webhook endpoints  
**So that** I receive real-time notifications about payment events

**Acceptance Criteria:**
- [ ] Merchant can set webhook URL
- [ ] Merchant can select which events to receive
- [ ] Webhook URL must be HTTPS
- [ ] System validates webhook URL is reachable
- [ ] Merchant can view webhook secret for signature verification

---

### US-2.5: Merchant Settings

**As a** merchant  
**I want to** configure my account settings  
**So that** I can customize PayFlow behavior for my business

**Acceptance Criteria:**
- [ ] Merchant can set default currency
- [ ] Merchant can enable/disable test mode
- [ ] Merchant can configure auto-capture settings
- [ ] Merchant can set settlement schedule preferences

---

## Functional Requirements

### FR-2.1: API Key Service

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-2.1.1 | Generate cryptographically secure API keys (256-bit) | Must |
| FR-2.1.2 | Store only SHA-256 hash of API key in database | Must |
| FR-2.1.3 | Support key prefix for identification (pk_live_, pk_test_) | Must |
| FR-2.1.4 | Enforce maximum 5 active keys per merchant | Should |
| FR-2.1.5 | Support key expiration (optional, configurable) | Could |

### FR-2.2: API Key Gateway Filter

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-2.2.1 | Validate API key in X-Api-Key header | Must |
| FR-2.2.2 | Extract merchant context from valid API key | Must |
| FR-2.2.3 | Support both API key and JWT authentication | Must |
| FR-2.2.4 | Apply per-key rate limiting | Should |
| FR-2.2.5 | Log API key usage metrics | Should |

### FR-2.3: Webhook Configuration

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-2.3.1 | Store webhook URL per merchant | Must |
| FR-2.3.2 | Generate unique webhook signing secret | Must |
| FR-2.3.3 | Support event type filtering | Should |
| FR-2.3.4 | Validate webhook URL accessibility | Should |

---

## Non-Functional Requirements

### NFR-2.1: Security

- API keys must be 256-bit cryptographically random
- Only hashed keys stored in database
- API key displayed only once to user
- Brute-force protection on key validation

### NFR-2.2: Performance

- Key validation < 10ms (cached lookup)
- Key generation < 100ms
- Support 1000+ API keys per merchant (future)

### NFR-2.3: Reliability

- Key revocation takes effect within 30 seconds
- No false positives on key validation
- Audit log for all key operations

---

## API Endpoints

### API Key Endpoints

```
POST   /api/v1/merchants/{id}/api-keys        - Generate new key
GET    /api/v1/merchants/{id}/api-keys        - List all keys
DELETE /api/v1/merchants/{id}/api-keys/{keyId} - Revoke key
POST   /api/v1/merchants/{id}/api-keys/{keyId}/rotate - Rotate key
```

### Webhook Endpoints

```
PUT    /api/v1/merchants/{id}/webhook         - Configure webhook
GET    /api/v1/merchants/{id}/webhook         - Get webhook config
DELETE /api/v1/merchants/{id}/webhook         - Remove webhook
POST   /api/v1/merchants/{id}/webhook/test    - Send test webhook
```

### Settings Endpoints

```
GET    /api/v1/merchants/{id}/settings        - Get settings
PUT    /api/v1/merchants/{id}/settings        - Update settings
```

---

## Data Models

### API Key Entity

```
api_keys
├── id (UUID, PK)
├── merchant_id (UUID, FK)
├── key_id (VARCHAR) - Visible identifier (pk_live_xxx)
├── key_hash (VARCHAR) - SHA-256 hash of full key
├── name (VARCHAR) - User-friendly label
├── permissions (ENUM) - READ, WRITE, FULL
├── last_used_at (TIMESTAMP)
├── expires_at (TIMESTAMP, nullable)
├── is_active (BOOLEAN)
├── created_at (TIMESTAMP)
├── revoked_at (TIMESTAMP, nullable)
└── revoked_by (UUID, nullable)
```

### Webhook Config Entity

```
merchant_webhooks
├── id (UUID, PK)
├── merchant_id (UUID, FK)
├── url (VARCHAR)
├── secret (VARCHAR) - HMAC signing key
├── events (VARCHAR[]) - Event types to send
├── is_active (BOOLEAN)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

### Merchant Settings Entity

```
merchant_settings
├── id (UUID, PK)
├── merchant_id (UUID, FK)
├── default_currency (VARCHAR)
├── auto_capture (BOOLEAN)
├── test_mode (BOOLEAN)
├── settlement_schedule (ENUM)
├── notification_email (VARCHAR)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

---

## Dependencies

### From Sprint 1

- Identity Service (JWT authentication)
- Merchant Service (merchant context)
- API Gateway (request routing)
- PostgreSQL (data storage)

### New Infrastructure

- Redis (API key caching)

---

## Out of Scope

- Webhook event delivery (Sprint 7)
- IP whitelisting for API keys
- Scoped permissions (resource-level)
- API key usage analytics dashboard

---

## Success Criteria

| Metric | Target |
|--------|--------|
| API key generation | < 100ms |
| Key validation (cached) | < 5ms |
| Key validation (DB lookup) | < 50ms |
| Test coverage | > 80% |
| All acceptance criteria | 100% passed |

---

## Related Documents

- [Design Document](./design.md)
- [Task List](./tasks.md)
- [Sprint 1 Reference](../sprint-01-auth-onboarding/requirements.md)

---

**Next:** [Design Document](./design.md)
