-- V1: Create merchant and api_keys tables

CREATE TABLE IF NOT EXISTS merchant.merchants (
    id                      VARCHAR(50) PRIMARY KEY,
    user_id                 VARCHAR(50) NOT NULL,
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

CREATE TABLE IF NOT EXISTS merchant.api_keys (
    id              VARCHAR(50) PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    key_type        VARCHAR(10) NOT NULL,
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
