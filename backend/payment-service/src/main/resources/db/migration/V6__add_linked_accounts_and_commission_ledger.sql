CREATE TABLE IF NOT EXISTS payments.linked_accounts (
    payee_user_id             UUID PRIMARY KEY,
    payee_role                TEXT NOT NULL,
    razorpay_account_id       TEXT NOT NULL,
    kyc_status                TEXT NOT NULL,
    pending_clawback_balance  NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payments.platform_commission_ledger (
    ledger_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id      UUID NOT NULL,
    period_start     DATE NOT NULL,
    period_end       DATE NOT NULL,
    original_amount  NUMERIC(12,2) NOT NULL CHECK (original_amount >= 0),
    commission_pct   NUMERIC(5,2) NOT NULL CHECK (commission_pct >= 0),
    commission_kept  NUMERIC(12,2) NOT NULL CHECK (commission_kept >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_commission_ledger_provider_period UNIQUE (provider_id, period_start, period_end)
);

ALTER TABLE payments.payouts ADD COLUMN IF NOT EXISTS razorpay_transfer_id TEXT;

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE payments.linked_accounts TO payment_service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE payments.platform_commission_ledger TO payment_service_role;
