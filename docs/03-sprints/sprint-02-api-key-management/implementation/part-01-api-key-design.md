# Sprint 2, Part 01: API Key Design & Architecture

**Duration:** 30 minutes  
**Prerequisites:** Sprint 1 completed  
**Goal:** Understand the API key architecture and what we're building

---

## 1. Learning Objectives

By the end of this part, you will understand:
- Why payment gateways use API keys instead of just JWTs
- The difference between public keys and secret keys
- How API key authentication flows through the system
- The security model for key storage and validation

---

## 2. Why API Keys?

### 2.1 JWT vs API Key Authentication

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JWT vs API KEY AUTHENTICATION                             │
│                                                                              │
│  JWT (JSON Web Token)                    API Key                             │
│  ────────────────────                    ───────                             │
│  • Short-lived (expires)                 • Long-lived (until revoked)        │
│  • Contains user claims                  • Simple identifier                 │
│  • Self-verifiable                       • Requires DB/cache lookup          │
│  • Good for: User sessions               • Good for: Server-to-server        │
│  • Example: Dashboard login              • Example: Payment API calls        │
│                                                                              │
│  PayFlow Use Cases:                                                          │
│  ─────────────────                                                          │
│  Dashboard Login → JWT (user logs in, token expires in 24h)                 │
│  Payment API     → API Key (merchant server calls, key lives forever)       │
│                                                                              │
│  Why not JWT for API calls?                                                  │
│  ──────────────────────────                                                 │
│  Merchant's server needs to make API calls 24/7.                            │
│  JWT expires → server stops working → merchant loses money.                 │
│  API key never expires → always works → reliable integration.               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Public Key vs Secret Key

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PUBLIC KEY vs SECRET KEY                                  │
│                                                                              │
│  PUBLIC KEY (pk_test_xxx / pk_live_xxx)                                     │
│  ──────────────────────────────────────                                     │
│  • Can be exposed in frontend code                                          │
│  • Used for: Card tokenization, checkout pages                              │
│  • Limited permissions (read-only operations)                               │
│  • Safe to include in JavaScript                                            │
│                                                                              │
│  SECRET KEY (sk_test_xxx / sk_live_xxx)                                     │
│  ──────────────────────────────────────                                     │
│  • NEVER expose in frontend                                                 │
│  • Used for: Creating payments, refunds, capturing                          │
│  • Full permissions (read + write)                                          │
│  • Store ONLY on your server                                                │
│                                                                              │
│  Real-World Example (Stripe-like):                                          │
│  ─────────────────────────────────                                          │
│  Checkout page (JavaScript): pk_live_abc123...  → tokenize card            │
│  Your server (Node.js):      sk_live_xyz789...  → charge the card          │
│                                                                              │
│  If secret key leaks: Attacker can drain merchant's funds!                  │
│  If public key leaks: Attacker can only tokenize cards (useless alone)     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. API Key Format

### 3.1 Key Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY FORMAT                                            │
│                                                                              │
│  Format: {type}_{environment}_{random_string}                               │
│                                                                              │
│  Examples:                                                                   │
│  ─────────                                                                  │
│  pk_test_5G8nK2mPq9vX3hJ7LkYw    (public key, test mode)                   │
│  pk_live_9X2mN7pQk3vH5jL8RtUi    (public key, live mode)                   │
│  sk_test_EXAMPLE_DO_NOT_USE_123   (secret key, test mode)                   │
│  sk_live_EXAMPLE_DO_NOT_USE_456   (secret key, live mode)                   │
│                                                                              │
│  Breakdown:                                                                  │
│  ──────────                                                                 │
│  pk_ / sk_     → Type (public/secret)                                       │
│  test_ / live_ → Environment (sandbox/production)                          │
│  random_string → Unique identifier (URL-safe Base64)                        │
│                                                                              │
│  Length:                                                                     │
│  ────────                                                                   │
│  Public key:  pk_ (3) + test_/live_ (5) + random (20) = 28 chars           │
│  Secret key:  sk_ (3) + test_/live_ (5) + random (32) = 40 chars           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Key Storage Security

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECRET KEY STORAGE                                        │
│                                                                              │
│  NEVER store the secret key in plaintext!                                   │
│                                                                              │
│  What we store in database:                                                  │
│  ──────────────────────────                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │ id          │ key_abc123                                        │       │
│  │ merchant_id │ merch_xyz789                                      │       │
│  │ key_type    │ TEST                                              │       │
│  │ public_key  │ pk_test_5G8nK2mPq9vX3hJ7LkYw (stored as-is)      │       │
│  │ secret_hash │ e3b0c44298fc1c149afbf4c8996fb92427ae41e... (SHA-256)│     │
│  │ key_prefix  │ sk_test_abc1 (first 12 chars for display)        │       │
│  │ status      │ ACTIVE                                            │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│  Why hash the secret key?                                                    │
│  ────────────────────────                                                   │
│  If database is breached, attacker gets useless hashes.                    │
│  They cannot reverse SHA-256 to get actual secret keys.                    │
│  Same approach used for passwords.                                          │
│                                                                              │
│  Why store key_prefix?                                                       │
│  ─────────────────────                                                      │
│  Dashboard shows: "sk_test_abc1..." so merchant knows which key is which.  │
│  Without prefix: All keys look the same, impossible to manage.             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Authentication Flow Architecture

### 4.1 Complete Request Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY AUTHENTICATION FLOW                               │
│                                                                              │
│  Merchant's Server                                                          │
│       │                                                                      │
│       │ POST /v1/payments                                                   │
│       │ Headers:                                                            │
│       │   X-Api-Key: sk_live_EXAMPLE_KEY_DO_NOT_USE_1234567890   │
│       │   Content-Type: application/json                                    │
│       │ Body: {"amount": 1000, "currency": "INR", ...}                     │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                      API GATEWAY (:8080)                         │       │
│  │                                                                  │       │
│  │  ┌───────────────────────────────────────────────────────────┐ │       │
│  │  │ 1. CorrelationIdFilter (order: -2)                        │ │       │
│  │  │    Add X-Correlation-Id: req_abc123                       │ │       │
│  │  └───────────────────────────────────────────────────────────┘ │       │
│  │                         │                                       │       │
│  │                         ▼                                       │       │
│  │  ┌───────────────────────────────────────────────────────────┐ │       │
│  │  │ 2. RateLimitFilter (order: -1)                            │ │       │
│  │  │    Check: rate:sk_live_abc123... < 100/min?               │ │       │
│  │  │    If over limit → 429 Too Many Requests                  │ │       │
│  │  └───────────────────────────────────────────────────────────┘ │       │
│  │                         │                                       │       │
│  │                         ▼                                       │       │
│  │  ┌───────────────────────────────────────────────────────────┐ │       │
│  │  │ 3. ApiKeyAuthFilter (order: 0) ← NEW IN SPRINT 2          │ │       │
│  │  │    a. Extract X-Api-Key header                            │ │       │
│  │  │    b. Validate format (sk_test_ or sk_live_)              │ │       │
│  │  │    c. Hash key with SHA-256                               │ │       │
│  │  │    d. Check Redis cache (fast path)                       │ │       │
│  │  │    e. If miss: call merchant-service                      │ │       │
│  │  │    f. If valid: add X-Merchant-Id header                  │ │       │
│  │  │    g. If invalid: return 401 Unauthorized                 │ │       │
│  │  └───────────────────────────────────────────────────────────┘ │       │
│  │                         │                                       │       │
│  │                         ▼                                       │       │
│  │  Route to: PAYMENT-SERVICE                                      │       │
│  │  Headers: X-Merchant-Id: merch_xyz789 (added by filter)        │       │
│  │                                                                  │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                         │                                                   │
│                         ▼                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                   PAYMENT SERVICE (:8083)                        │       │
│  │                                                                  │       │
│  │  Trusts X-Merchant-Id header (gateway validated it)             │       │
│  │  Processes payment for merchant: merch_xyz789                   │       │
│  │                                                                  │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Caching Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REDIS CACHING FOR API KEYS                                │
│                                                                              │
│  Why cache?                                                                  │
│  ──────────                                                                 │
│  Without cache: Every API call = 1 DB query                                 │
│  1000 payments/sec × DB query = Database overload                          │
│                                                                              │
│  With cache: Most calls = Redis lookup (microseconds)                       │
│  Only cache misses hit database                                             │
│                                                                              │
│  Cache Structure:                                                            │
│  ────────────────                                                           │
│  Key:   apikey:{sha256_hash_of_secret_key}                                 │
│  Value: {merchantId}:{keyType}:{status}                                    │
│  TTL:   5 minutes (300 seconds)                                            │
│                                                                              │
│  Example:                                                                    │
│  ─────────                                                                  │
│  Key:   apikey:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934...          │
│  Value: merch_abc123:LIVE:ACTIVE                                            │
│  TTL:   300                                                                  │
│                                                                              │
│  Cache Flow:                                                                 │
│  ───────────                                                                │
│  Request → Check Redis                                                      │
│              │                                                              │
│     ┌────────┴────────┐                                                    │
│     ▼                 ▼                                                    │
│  Cache HIT         Cache MISS                                               │
│  (fast path)       │                                                       │
│     │              ▼                                                       │
│     │         Query DB via merchant-service                                │
│     │              │                                                       │
│     │              ▼                                                       │
│     │         Store in Redis (TTL: 5 min)                                  │
│     │              │                                                       │
│     └──────────────┴──────────────────┐                                    │
│                                       ▼                                    │
│                              Return merchantId                             │
│                                                                              │
│  TTL Trade-off:                                                              │
│  ──────────────                                                             │
│  Short TTL (1 min):  More DB queries, faster revocation                    │
│  Long TTL (60 min):  Fewer DB queries, delayed revocation                  │
│  Our choice (5 min): Balance of performance and security                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. What's Already Implemented (Sprint 1)

### 5.1 Existing Files

```
merchant-service/src/main/java/com/payflow/merchant/
├── model/
│   └── ApiKey.java                 ← Entity with KeyType, KeyStatus enums
├── repository/
│   └── ApiKeyRepository.java       ← Has findBySecretKeyHashAndStatus
├── service/
│   └── MerchantService.java        ← Has generateApiKey(), validateSecretKey()
└── controller/
    └── MerchantController.java     ← Has POST /api-keys endpoint
```

### 5.2 Existing ApiKey Entity

**File:** `merchant-service/src/main/java/com/payflow/merchant/model/ApiKey.java`

```java
@Entity
@Table(name = "api_keys", schema = "merchant")
public class ApiKey {
    @Id
    private String id;                    // key_Hk7mN3xQp2
    private String merchantId;            // merch_abc123
    
    @Enumerated(EnumType.STRING)
    private KeyType keyType;              // TEST or LIVE
    
    private String publicKey;             // pk_test_xxxx
    private String secretKeyHash;         // SHA-256 hash (never plaintext!)
    private String keyPrefix;             // sk_test_xxxx (first 12 chars)
    
    @Enumerated(EnumType.STRING)
    private KeyStatus status;             // ACTIVE or REVOKED
    
    private Instant lastUsedAt;
    private Instant createdAt;

    public enum KeyType { TEST, LIVE }
    public enum KeyStatus { ACTIVE, REVOKED }
}
```

---

## 6. What We'll Build in Sprint 2

| Part | Component | Description |
|------|-----------|-------------|
| 02 | API Key Generation | Review and enhance existing generation |
| 03 | ApiKeyAuthFilter | Gateway filter for authentication |
| 04 | Webhook Configuration | Webhook URL and secret management |
| 05 | Fee Plans | MDR configuration per merchant |
| 06 | Swagger Testing | Test all merchant endpoints |
| 07 | Frontend API Keys Page | React page for key management |
| 08 | Frontend Settings Page | Webhook and settings configuration |
| 09 | Docker Update | Add new services to compose |
| 10 | CI/CD & Postman | Automated testing pipeline |
| 11 | AWS Deployment | Deploy Sprint 2 changes |
| 12 | E2E Testing | End-to-end verification |
| 13 | Git & PR | Version control best practices |
| 14 | Sprint Summary | Review and documentation |

---

## 7. Database Schema

```sql
-- Already exists from Sprint 1 (V1 migration)
CREATE TABLE IF NOT EXISTS merchant.api_keys (
    id              VARCHAR(50) PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    key_type        VARCHAR(10) NOT NULL,        -- TEST or LIVE
    public_key      VARCHAR(100) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,       -- SHA-256 hash
    key_prefix      VARCHAR(30) NOT NULL,        -- For display: "sk_test_abc1..."
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_public ON merchant.api_keys(public_key);
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(secret_key_hash);
```

---

## 8. Key Takeaways

| Concept | Remember |
|---------|----------|
| **JWT vs API Key** | JWT for users, API key for servers |
| **Public vs Secret** | Public for frontend, secret for backend |
| **Hash storage** | NEVER store secret key plaintext |
| **Cache TTL** | 5 minutes balances performance/security |
| **Gateway auth** | Validate once at gateway, not in every service |

---

## 9. Next Steps

**Continue to:** [part-02-api-key-generation.md](./part-02-api-key-generation.md)

In the next part, you'll review and test the existing API key generation functionality.

---

**End of Sprint 2, Part 01**
