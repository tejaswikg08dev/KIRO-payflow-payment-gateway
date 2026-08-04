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
CREATE TABLE identity.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20) DEFAULT 'MERCHANT',
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    merchant_id     UUID,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at   TIMESTAMP
);

CREATE INDEX idx_users_email ON identity.users(email);
CREATE INDEX idx_users_merchant ON identity.users(merchant_id);
```

---

## 3. PostgreSQL Schema: merchant

### merchants

```sql
CREATE TABLE merchant.merchants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name       VARCHAR(255) NOT NULL,
    business_type       VARCHAR(50) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    phone               VARCHAR(20),
    website             VARCHAR(255),
    status              VARCHAR(20) DEFAULT 'PENDING',
    kyc_status          VARCHAR(20) DEFAULT 'PENDING',
    settlement_schedule VARCHAR(10) DEFAULT 'T+2',
    webhook_url         VARCHAR(500),
    webhook_secret      VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_email ON merchant.merchants(email);
CREATE INDEX idx_merchants_status ON merchant.merchants(status);
```

### api_keys

```sql
CREATE TABLE merchant.api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchant.merchants(id),
    key_prefix      VARCHAR(20) NOT NULL,
    key_hash        VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(100),
    environment     VARCHAR(10) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP
);

CREATE INDEX idx_api_keys_merchant ON merchant.api_keys(merchant_id);
CREATE INDEX idx_api_keys_hash ON merchant.api_keys(key_hash);
```

---

## 4. PostgreSQL Schema: payment

### orders

```sql
CREATE TABLE payment.orders (
    id                  VARCHAR(30) PRIMARY KEY,
    merchant_id         UUID NOT NULL,
    amount              BIGINT NOT NULL,
    currency            VARCHAR(3) DEFAULT 'INR',
    status              VARCHAR(20) DEFAULT 'CREATED',
    description         VARCHAR(500),
    merchant_order_id   VARCHAR(100),
    customer_email      VARCHAR(255),
    customer_phone      VARCHAR(20),
    metadata            JSONB,
    expires_at          TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_merchant ON payment.orders(merchant_id);
CREATE INDEX idx_orders_status ON payment.orders(status);
CREATE INDEX idx_orders_created ON payment.orders(created_at);
```


### payments

```sql
CREATE TABLE payment.payments (
    id                  VARCHAR(30) PRIMARY KEY,
    order_id            VARCHAR(30) NOT NULL REFERENCES payment.orders(id),
    merchant_id         UUID NOT NULL,
    amount              BIGINT NOT NULL,
    currency            VARCHAR(3) DEFAULT 'INR',
    status              VARCHAR(20) DEFAULT 'CREATED',
    payment_method      VARCHAR(20) NOT NULL,
    card_last_four      VARCHAR(4),
    card_brand          VARCHAR(20),
    upi_vpa             VARCHAR(100),
    bank_code           VARCHAR(20),
    auth_code           VARCHAR(20),
    rrn                 VARCHAR(30),
    acquirer_code       VARCHAR(10),
    fraud_score         INTEGER,
    error_code          VARCHAR(20),
    error_message       VARCHAR(500),
    idempotency_key     VARCHAR(64) UNIQUE,
    captured_amount     BIGINT DEFAULT 0,
    refunded_amount     BIGINT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    authorized_at       TIMESTAMP,
    captured_at         TIMESTAMP
);

CREATE INDEX idx_payments_order ON payment.payments(order_id);
CREATE INDEX idx_payments_merchant ON payment.payments(merchant_id);
CREATE INDEX idx_payments_status ON payment.payments(status);
CREATE INDEX idx_payments_idempotency ON payment.payments(idempotency_key);
```

### refunds

```sql
CREATE TABLE payment.refunds (
    id              VARCHAR(30) PRIMARY KEY,
    payment_id      VARCHAR(30) NOT NULL REFERENCES payment.payments(id),
    merchant_id     UUID NOT NULL,
    amount          BIGINT NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    reason          VARCHAR(500),
    rrn             VARCHAR(30),
    idempotency_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at    TIMESTAMP
);

CREATE INDEX idx_refunds_payment ON payment.refunds(payment_id);
CREATE INDEX idx_refunds_merchant ON payment.refunds(merchant_id);
```

---

## 5. PostgreSQL Schema: settlement

### settlements

```sql
CREATE TABLE settlement.settlements (
    id              VARCHAR(30) PRIMARY KEY,
    merchant_id     UUID NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    gross_amount    BIGINT NOT NULL,
    fee_amount      BIGINT NOT NULL,
    tax_amount      BIGINT NOT NULL,
    net_amount      BIGINT NOT NULL,
    transaction_count INTEGER NOT NULL,
    refund_count    INTEGER DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',
    payout_reference VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settled_at      TIMESTAMP
);

CREATE INDEX idx_settlements_merchant ON settlement.settlements(merchant_id);
CREATE INDEX idx_settlements_period ON settlement.settlements(period_start, period_end);
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
│   │  users   │ N:1     │ merchants│ 1:N     │ api_keys │                   │
│   │          │────────►│          │────────►│          │                   │
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
