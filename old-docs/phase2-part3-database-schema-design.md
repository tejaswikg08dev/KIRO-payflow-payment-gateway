# Phase 2 — Part 3: Database Schema Design

> Complete SQL DDL for all tables. These will become Flyway migrations.
> One PostgreSQL instance with separate schemas per service.

---

## 1. Database Strategy

```
Single PostgreSQL Instance (RDS db.t3.micro)
├── Schema: identity    → Users, roles, tokens
├── Schema: merchant    → Merchants, API keys, fee configs
├── Schema: payment     → Orders, payments, refunds, state history
└── Schema: settlement  → Settlements, payouts, settlement items

Separate DynamoDB Tables (Always Free):
├── Table: webhook_events
├── Table: routing_metrics
└── Table: audit_trail
```

**Why separate schemas (not separate databases)?**
- Cost: One RDS instance is $15/month vs $60/month for four
- Logical separation: Each service only accesses its own schema
- In production: Would be separate databases (we design for that)

---

## 2. Identity Service Schema

```sql
-- Schema: identity

CREATE SCHEMA IF NOT EXISTS identity;

-- Roles table
CREATE TABLE identity.roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed roles
INSERT INTO identity.roles (name, description) VALUES
    ('CUSTOMER', 'End customer making payments'),
    ('MERCHANT', 'Business accepting payments'),
    ('ADMIN', 'Platform administrator');

-- Users table
CREATE TABLE identity.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role_id         INTEGER NOT NULL REFERENCES identity.roles(id),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, DELETED
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON identity.users(email);
CREATE INDEX idx_users_status ON identity.users(status);

-- Refresh tokens (for JWT refresh flow)
CREATE TABLE identity.refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    token       VARCHAR(500) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON identity.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON identity.refresh_tokens(token);
```

---

## 3. Merchant Service Schema

```sql
-- Schema: merchant

CREATE SCHEMA IF NOT EXISTS merchant;

-- Merchants table
CREATE TABLE merchant.merchants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,  -- References identity.users (cross-schema FK not enforced)
    business_name           VARCHAR(200) NOT NULL,
    business_type           VARCHAR(50) NOT NULL,  -- INDIVIDUAL, PARTNERSHIP, COMPANY
    registration_number     VARCHAR(100),
    gst_number              VARCHAR(20),
    website_url             VARCHAR(500),
    logo_url                VARCHAR(500),
    callback_url            VARCHAR(500),  -- Redirect after checkout
    webhook_url             VARCHAR(500),  -- Webhook delivery endpoint
    webhook_secret          VARCHAR(255),  -- HMAC secret for signing
    settlement_schedule     VARCHAR(10) NOT NULL DEFAULT 'T+2',  -- T+1, T+2, T+3, WEEKLY
    mdr_percentage          DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    fixed_fee_amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    bank_account_number     VARCHAR(30),
    bank_ifsc_code          VARCHAR(15),
    bank_account_holder     VARCHAR(200),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, ACTIVE, SUSPENDED
    kyc_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    kyc_verified_at         TIMESTAMP,
    activated_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_user ON merchant.merchants(user_id);
CREATE INDEX idx_merchants_status ON merchant.merchants(status);

-- API Keys table
CREATE TABLE merchant.api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchant.merchants(id) ON DELETE CASCADE,
    key_type        VARCHAR(10) NOT NULL,  -- TEST, LIVE
    public_key      VARCHAR(100) NOT NULL UNIQUE,  -- pk_tst_xxx or pk_pay_xxx
    secret_key_hash VARCHAR(255) NOT NULL,  -- SHA-256 hash of sk_tst_xxx
    key_prefix      VARCHAR(30) NOT NULL,   -- First 8 chars for identification
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, REVOKED
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_public ON merchant.api_keys(public_key);
CREATE INDEX idx_api_keys_prefix ON merchant.api_keys(key_prefix);

-- Fee configuration per payment method
CREATE TABLE merchant.fee_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchant.merchants(id) ON DELETE CASCADE,
    payment_method  VARCHAR(20) NOT NULL,  -- CARD_CREDIT, CARD_DEBIT, UPI, NETBANKING
    fee_type        VARCHAR(10) NOT NULL,  -- PERCENTAGE, FIXED, BOTH
    percentage_fee  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    fixed_fee       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(merchant_id, payment_method)
);
```


---

## 4. Payment Service Schema

```sql
-- Schema: payment

CREATE SCHEMA IF NOT EXISTS payment;

-- Orders table (merchant creates an order before payment)
CREATE TABLE payment.orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    receipt         VARCHAR(100),  -- Merchant's internal reference
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',  -- CREATED, PAID, EXPIRED
    notes           JSONB,  -- Flexible key-value metadata
    expires_at      TIMESTAMP NOT NULL,  -- Auto-expire after 30 min
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_merchant ON payment.orders(merchant_id);
CREATE INDEX idx_orders_status ON payment.orders(status);
CREATE INDEX idx_orders_expires ON payment.orders(expires_at) WHERE status = 'CREATED';
CREATE INDEX idx_orders_receipt ON payment.orders(merchant_id, receipt);

-- Payments table (core transaction record)
CREATE TABLE payment.payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES payment.orders(id),
    merchant_id         UUID NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    -- CREATED, PROCESSING, AUTHORIZED, CAPTURED, SETTLED, VOIDED, REFUNDED, FAILED, EXPIRED
    payment_method      VARCHAR(20) NOT NULL,  -- CARD, UPI, NETBANKING, WALLET
    
    -- Card details (only last4 stored, never full PAN)
    card_last4          VARCHAR(4),
    card_network        VARCHAR(20),  -- VISA, MASTERCARD, RUPAY
    card_type           VARCHAR(10),  -- CREDIT, DEBIT
    card_issuer_bank    VARCHAR(100),
    
    -- UPI details
    upi_vpa             VARCHAR(100),
    
    -- Net Banking details
    bank_name           VARCHAR(100),
    
    -- Authorization details
    auth_code           VARCHAR(10),
    rrn                 VARCHAR(20),  -- Retrieval Reference Number
    stan                VARCHAR(10),  -- System Trace Audit Number
    
    -- Processing metadata
    idempotency_key     VARCHAR(100),
    risk_score          INTEGER,  -- 0-100 from fraud engine
    fraud_decision      VARCHAR(20),  -- APPROVE, CHALLENGE, REVIEW, DECLINE
    route_id            VARCHAR(50),  -- Which bank route was used
    
    -- Amounts
    captured_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    refunded_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    
    -- Timestamps
    authorized_at       TIMESTAMP,
    captured_at         TIMESTAMP,
    settled_at          TIMESTAMP,
    voided_at           TIMESTAMP,
    failed_at           TIMESTAMP,
    
    -- Error info (if failed)
    failure_code        VARCHAR(20),
    failure_reason      VARCHAR(500),
    bank_response_code  VARCHAR(5),
    
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payment.payments(order_id);
CREATE INDEX idx_payments_merchant ON payment.payments(merchant_id);
CREATE INDEX idx_payments_status ON payment.payments(status);
CREATE INDEX idx_payments_idempotency ON payment.payments(merchant_id, idempotency_key);
CREATE INDEX idx_payments_created ON payment.payments(created_at DESC);
CREATE INDEX idx_payments_captured_unsettled ON payment.payments(captured_at)
    WHERE status = 'CAPTURED';

-- Refunds table
CREATE TABLE payment.refunds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payment.payments(id),
    merchant_id     UUID NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'INITIATED',  -- INITIATED, PROCESSED, FAILED
    reason          VARCHAR(500),
    rrn             VARCHAR(20),
    bank_response   VARCHAR(5),
    notes           JSONB,
    initiated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,
    failed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_payment ON payment.refunds(payment_id);
CREATE INDEX idx_refunds_merchant ON payment.refunds(merchant_id);
CREATE INDEX idx_refunds_status ON payment.refunds(status);

-- Payment state history (audit trail of every transition)
CREATE TABLE payment.payment_state_history (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      UUID NOT NULL REFERENCES payment.payments(id),
    from_state      VARCHAR(20),  -- NULL for initial creation
    to_state        VARCHAR(20) NOT NULL,
    event           VARCHAR(30) NOT NULL,  -- AUTHORIZE, CAPTURE, VOID, REFUND, SETTLE, FAIL, EXPIRE
    metadata        JSONB,  -- Additional context (auth_code, reason, etc.)
    actor           VARCHAR(100),  -- Who triggered (merchant_id, system, scheduler)
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_state_history_payment ON payment.payment_state_history(payment_id);
CREATE INDEX idx_state_history_created ON payment.payment_state_history(created_at DESC);
```

---

## 5. Settlement Service Schema

```sql
-- Schema: settlement

CREATE SCHEMA IF NOT EXISTS settlement;

-- Settlements table (one per merchant per day)
CREATE TABLE settlement.settlements (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id             UUID NOT NULL,
    settlement_date         DATE NOT NULL,
    
    -- Amounts
    gross_amount            DECIMAL(14,2) NOT NULL,  -- Total captured
    refund_amount           DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    fee_amount              DECIMAL(14,2) NOT NULL,  -- MDR
    gst_on_fee              DECIMAL(14,2) NOT NULL,  -- 18% GST on fee
    net_amount              DECIMAL(14,2) NOT NULL,  -- Merchant receives
    
    -- Counts
    total_transactions      INTEGER NOT NULL DEFAULT 0,
    total_refunds           INTEGER NOT NULL DEFAULT 0,
    
    -- Processing
    status                  VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    -- INITIATED, PROCESSING, PROCESSED, PAYOUT_PENDING, COMPLETED, FAILED
    payout_reference        VARCHAR(100),
    payout_utr              VARCHAR(50),  -- UTR from bank transfer
    
    -- Timestamps
    processed_at            TIMESTAMP,
    payout_initiated_at     TIMESTAMP,
    payout_completed_at     TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE(merchant_id, settlement_date)
);

CREATE INDEX idx_settlements_merchant ON settlement.settlements(merchant_id);
CREATE INDEX idx_settlements_date ON settlement.settlements(settlement_date DESC);
CREATE INDEX idx_settlements_status ON settlement.settlements(status);

-- Settlement items (individual payments in a settlement)
CREATE TABLE settlement.settlement_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id   UUID NOT NULL REFERENCES settlement.settlements(id),
    payment_id      UUID NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    fee_deducted    DECIMAL(12,2) NOT NULL,
    gst_deducted    DECIMAL(12,2) NOT NULL,
    net_amount      DECIMAL(12,2) NOT NULL,
    type            VARCHAR(10) NOT NULL,  -- PAYMENT, REFUND
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_items_settlement ON settlement.settlement_items(settlement_id);
CREATE INDEX idx_settlement_items_payment ON settlement.settlement_items(payment_id);

-- Payouts table (bank transfers to merchants)
CREATE TABLE settlement.payouts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id       UUID NOT NULL REFERENCES settlement.settlements(id),
    merchant_id         UUID NOT NULL,
    amount              DECIMAL(14,2) NOT NULL,
    bank_account_number VARCHAR(30) NOT NULL,
    ifsc_code           VARCHAR(15) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    utr_number          VARCHAR(50),  -- Unique Transaction Reference from bank
    failure_reason      VARCHAR(500),
    initiated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP,
    failed_at           TIMESTAMP
);

CREATE INDEX idx_payouts_settlement ON settlement.payouts(settlement_id);
CREATE INDEX idx_payouts_merchant ON settlement.payouts(merchant_id);
CREATE INDEX idx_payouts_status ON settlement.payouts(status);
```

---

## 6. DynamoDB Table Designs

### 6.1 webhook_events Table

```
Table Name: webhook_events
Billing Mode: PAY_PER_REQUEST (on-demand, no capacity planning)

Partition Key: event_id (String, UUID)
Sort Key: created_at (String, ISO timestamp)

Attributes:
├── event_id (S): Unique event identifier
├── merchant_id (S): Which merchant this is for
├── event_type (S): payment.authorized, payment.captured, refund.created, etc.
├── payload (S): JSON string of webhook body
├── webhook_url (S): Where to deliver
├── signature (S): HMAC-SHA256 signature
├── delivery_status (S): PENDING, DELIVERED, FAILED, DLQ
├── attempt_count (N): Number of delivery attempts (0-5)
├── last_attempt_at (S): Timestamp of last attempt
├── next_retry_at (S): When to retry next
├── response_code (N): HTTP response from merchant (200, 500, etc.)
├── response_body (S): First 500 chars of response
├── created_at (S): When event was created
└── expires_at (N): TTL — auto-delete after 30 days (epoch seconds)

GSI-1: merchant_id-index
├── Partition Key: merchant_id
├── Sort Key: created_at
└── Purpose: "Show all events for merchant X"

GSI-2: status-retry-index
├── Partition Key: delivery_status
├── Sort Key: next_retry_at
└── Purpose: "Find all PENDING events that need retry NOW"
```

### 6.2 routing_metrics Table

```
Table Name: routing_metrics
Billing Mode: PAY_PER_REQUEST

Partition Key: route_id (String)
Sort Key: time_bucket (String, hourly: "2026-07-19T14")

Attributes:
├── route_id (S): Bank/acquirer identifier (e.g., "HDFC_ACQ_01")
├── time_bucket (S): Hour-level granularity
├── total_attempts (N): Total transactions attempted
├── success_count (N): Approved count
├── failure_count (N): Declined/error count
├── timeout_count (N): Timeout count
├── avg_latency_ms (N): Average response time
├── success_rate (N): success_count / total_attempts
├── last_failure_reason (S): Most recent failure
├── last_updated (S): Timestamp
└── expires_at (N): TTL — auto-delete after 7 days

Purpose: Routing engine reads this to decide best route
Updated: After every transaction attempt
```

### 6.3 audit_trail Table

```
Table Name: audit_trail
Billing Mode: PAY_PER_REQUEST

Partition Key: entity_id (String, e.g., "pay_abc123" or "merch_xyz")
Sort Key: timestamp_action (String, e.g., "2026-07-19T14:30:00Z#CAPTURED")

Attributes:
├── entity_id (S): Payment ID, merchant ID, or user ID
├── entity_type (S): PAYMENT, MERCHANT, USER
├── action (S): CREATED, AUTHORIZED, CAPTURED, REFUNDED, etc.
├── actor (S): Who did this (user_id, merchant_id, "SYSTEM", "SCHEDULER")
├── old_value (S): Previous state/value (JSON)
├── new_value (S): New state/value (JSON)
├── ip_address (S): Request IP
├── user_agent (S): Browser/client info
├── metadata (S): Additional context (JSON)
├── created_at (S): Timestamp
└── expires_at (N): TTL — auto-delete after 90 days
```

---

## 7. Index Strategy Summary

| Table | Index | Purpose | Query Pattern |
|-------|-------|---------|--------------|
| payments | merchant_id | List merchant's payments | WHERE merchant_id = ? |
| payments | status | Find payments by state | WHERE status = 'CAPTURED' |
| payments | idempotency | Duplicate check | WHERE merchant_id = ? AND idempotency_key = ? |
| payments | captured unsettled | Settlement batch read | WHERE status = 'CAPTURED' AND captured_at < ? |
| orders | expires_at (partial) | Auto-expire job | WHERE status = 'CREATED' AND expires_at < NOW() |
| settlements | date | Daily settlement lookup | WHERE settlement_date = ? |
| api_keys | public_key | API key authentication | WHERE public_key = ? |

---

## 8. Data Retention Policy

| Data | Retention | Method |
|------|-----------|--------|
| Payments | 7 years | Archive to S3 after 1 year |
| Settlement records | 7 years | Keep in PostgreSQL |
| Webhook events | 30 days | DynamoDB TTL auto-delete |
| Routing metrics | 7 days | DynamoDB TTL auto-delete |
| Audit trail | 90 days | DynamoDB TTL auto-delete |
| Refresh tokens | Until expiry | Auto-cleanup scheduled job |

---

## 9. Interview Questions This Document Answers

1. **"Design the database for a payment system"** → Full schema above
2. **"How do you prevent duplicate payments at DB level?"** → Unique index on (merchant_id, idempotency_key)
3. **"Why UUID for primary keys?"** → Globally unique across services, no sequential guessing
4. **"How do you handle financial precision?"** → DECIMAL(12,2), never FLOAT
5. **"Why separate schemas?"** → Logical isolation, same as separate DBs conceptually
6. **"How do you handle audit?"** → payment_state_history + DynamoDB audit_trail
7. **"Why DynamoDB for events?"** → High write throughput, TTL auto-delete, always free
8. **"How does settlement find captured payments?"** → Partial index on captured_at WHERE status = 'CAPTURED'

---

## Next Step

→ Continue to **`phase2-part4-api-specification.md`**
