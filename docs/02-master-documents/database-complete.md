# PayFlow — Complete Database Design Document

**Document Version:** 2.0  
**Last Updated:** August 2026  
**Purpose:** Master reference for all database schemas, tables, and relationships

---

## Document Overview

This document provides the **complete database design** for PayFlow, including:
- PostgreSQL schemas and tables
- Redis data structures
- DynamoDB tables
- Entity relationships and indexes

---

## 1. Database Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PayFlow Database Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   PostgreSQL (Relational - 4 schemas)                                       │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ identity    │ merchant    │ payment     │ settlement            │       │
│   │ • users     │ • merchants │ • orders    │ • settlements         │       │
│   │ • roles     │ • api_keys  │ • payments  │ • settlement_items    │       │
│   │             │ • settings  │ • refunds   │ • payout_history      │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   Redis (Cache/Session)                                                     │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ idempotency_keys │ rate_limits │ route_cache │ session_data    │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   DynamoDB (NoSQL)                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │ webhook_events   │ routing_metrics │ audit_trail               │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. PostgreSQL Schema: identity


### users

```sql
CREATE TABLE IF NOT EXISTS identity.users (
    id              VARCHAR(50) PRIMARY KEY,   -- usr_xxxxxxxxxx format
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON identity.users(email);
CREATE INDEX idx_users_status ON identity.users(status);
```

---

## 3. PostgreSQL Schema: merchant

### merchants

```sql
CREATE TABLE IF NOT EXISTS merchant.merchants (
    id                      VARCHAR(50) PRIMARY KEY,  -- merch_xxxxxxxxxx format
    user_id                 VARCHAR(50) NOT NULL,     -- Reference to identity.users
    business_name           VARCHAR(200) NOT NULL,
    business_type           VARCHAR(50) NOT NULL,
    registration_number     VARCHAR(100),
    gst_number              VARCHAR(20),
    website_url             VARCHAR(500),
    callback_url            VARCHAR(500),
    webhook_url             VARCHAR(500),
    webhook_secret          VARCHAR(255),
    settlement_schedule     VARCHAR(10) NOT NULL DEFAULT 'T+2',
    mdr_percentage          DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    bank_account_number     VARCHAR(30),
    bank_ifsc_code          VARCHAR(15),
    bank_account_holder     VARCHAR(200),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_user ON merchant.merchants(user_id);
CREATE INDEX idx_merchants_status ON merchant.merchants(status);
```

### api_keys

```sql
CREATE TABLE IF NOT EXISTS merchant.api_keys (
    id              VARCHAR(50) PRIMARY KEY,  -- key_xxxxxxxxxx format
    merchant_id     VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    key_type        VARCHAR(10) NOT NULL,     -- 'live' or 'test'
    public_key      VARCHAR(100) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,
    key_prefix      VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_public ON merchant.api_keys(public_key);
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(secret_key_hash);
```

---

## 4. PostgreSQL Schema: payment

### orders

```sql
CREATE TABLE IF NOT EXISTS payment.orders (
    id              VARCHAR(50) PRIMARY KEY,   -- ord_xxxxxxxxxx format
    merchant_id     VARCHAR(50) NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    receipt         VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    notes           JSONB,
    expires_at      TIMESTAMP NOT NULL,
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_merchant ON payment.orders(merchant_id);
CREATE INDEX idx_orders_status ON payment.orders(status);
```


### payments

```sql
CREATE TABLE IF NOT EXISTS payment.payments (
    id                  VARCHAR(50) PRIMARY KEY,  -- pay_xxxxxxxxxx format
    order_id            VARCHAR(50) NOT NULL REFERENCES payment.orders(id),
    merchant_id         VARCHAR(50) NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    payment_method      VARCHAR(20) NOT NULL,
    card_last4          VARCHAR(4),
    card_network        VARCHAR(20),
    upi_vpa             VARCHAR(100),
    auth_code           VARCHAR(10),
    rrn                 VARCHAR(20),
    idempotency_key     VARCHAR(100),
    risk_score          INTEGER,
    route_id            VARCHAR(50),
    captured_amount     DECIMAL(12,2) DEFAULT 0.00,
    refunded_amount     DECIMAL(12,2) DEFAULT 0.00,
    failure_code        VARCHAR(50),
    failure_reason      VARCHAR(500),
    authorized_at       TIMESTAMP,
    captured_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payment.payments(order_id);
CREATE INDEX idx_payments_merchant ON payment.payments(merchant_id);
CREATE INDEX idx_payments_status ON payment.payments(status);
CREATE INDEX idx_payments_idempotency ON payment.payments(merchant_id, idempotency_key);
```

### refunds

```sql
CREATE TABLE IF NOT EXISTS payment.refunds (
    id              VARCHAR(50) PRIMARY KEY,  -- rfnd_xxxxxxxxxx format
    payment_id      VARCHAR(50) NOT NULL REFERENCES payment.payments(id),
    merchant_id     VARCHAR(50) NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    reason          VARCHAR(500),
    rrn             VARCHAR(20),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP
);

CREATE INDEX idx_refunds_payment ON payment.refunds(payment_id);
```

---

## 5. PostgreSQL Schema: settlement

### settlements

```sql
CREATE TABLE IF NOT EXISTS settlement.settlements (
    id                      VARCHAR(50) PRIMARY KEY,  -- stl_xxxxxxxxxx format
    merchant_id             VARCHAR(50) NOT NULL,
    settlement_date         DATE NOT NULL,
    gross_amount            DECIMAL(14,2) NOT NULL,
    refund_amount           DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    fee_amount              DECIMAL(14,2) NOT NULL,
    gst_on_fee              DECIMAL(14,2) NOT NULL,
    net_amount              DECIMAL(14,2) NOT NULL,
    total_transactions      INTEGER NOT NULL DEFAULT 0,
    total_refunds           INTEGER NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    payout_utr              VARCHAR(50),
    processed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(merchant_id, settlement_date)
);

CREATE INDEX idx_settlements_merchant ON settlement.settlements(merchant_id);
CREATE INDEX idx_settlements_date ON settlement.settlements(settlement_date DESC);
CREATE INDEX idx_settlements_status ON settlement.settlements(status);
```

---

## 6. Redis Data Structures

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `idempotency:{key}` | String | 24h | Prevent duplicate payments |
| `rate_limit:{merchant_id}` | Sorted Set | 1min | Rate limiting counters |
| `route_cache:{card_bin}` | Hash | 5min | Cache routing decisions |
| `jwt_blacklist:{token_id}` | String | Token expiry | Revoked tokens |

---

## 7. DynamoDB Tables

### webhook_events

| Attribute | Type | Key |
|-----------|------|-----|
| event_id | String | Partition Key |
| merchant_id | String | GSI Partition Key |
| created_at | String | GSI Sort Key |
| event_type | String | |
| payload | Map | |
| delivery_status | String | |
| attempts | Number | |

---

## 8. Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Entity Relationships                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────┐         ┌──────────┐         ┌──────────┐                   │
│   │  users   │ 1:N     │ merchants│ 1:N     │ api_keys │                   │
│   │          │◄────────│ (user_id)│────────►│          │                   │
│   └──────────┘         └──────────┘         └──────────┘                   │
│                              │                                               │
│                              │ 1:N                                          │
│                              ▼                                               │
│                        ┌──────────┐                                         │
│                        │  orders  │                                         │
│                        └────┬─────┘                                         │
│                             │ 1:N                                           │
│                             ▼                                               │
│                        ┌──────────┐         ┌──────────┐                   │
│                        │ payments │ 1:N     │ refunds  │                   │
│                        │          │────────►│          │                   │
│                        └──────────┘         └──────────┘                   │
│                             │                                               │
│                             │ N:1                                           │
│                             ▼                                               │
│                        ┌──────────┐                                         │
│                        │settlements│                                        │
│                        └──────────┘                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Next Document

**Continue to:** [api-complete.md](./api-complete.md) — API Reference

---

**End of Database Document**
