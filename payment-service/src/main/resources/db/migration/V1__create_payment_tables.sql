-- V1: Create orders and payments tables

CREATE TABLE IF NOT EXISTS payment.orders (
    id              VARCHAR(50) PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS payment.payments (
    id                  VARCHAR(50) PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS payment.refunds (
    id              VARCHAR(50) PRIMARY KEY,
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
