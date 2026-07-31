CREATE TABLE IF NOT EXISTS orders.order_compensations (
    compensation_id UUID PRIMARY KEY,
    order_id UUID NULL,
    customer_id UUID NOT NULL,
    coupon_code VARCHAR(64) NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_order_compensations_pending
    ON orders.order_compensations(status, next_attempt_at, created_at);
