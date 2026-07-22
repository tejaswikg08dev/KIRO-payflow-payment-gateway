-- V1: Create settlement tables

CREATE TABLE IF NOT EXISTS settlement.settlements (
    id                      VARCHAR(50) PRIMARY KEY,
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
