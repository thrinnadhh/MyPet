CREATE SCHEMA IF NOT EXISTS payments;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS payments.transactions (
    transaction_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    transaction_type        TEXT NOT NULL,
    reference_id            UUID NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency                TEXT NOT NULL DEFAULT 'INR',
    status                  TEXT NOT NULL DEFAULT 'PENDING',
    gateway                 TEXT NOT NULL DEFAULT 'RAZORPAY',
    gateway_transaction_id  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user
    ON payments.transactions(user_id);

CREATE INDEX IF NOT EXISTS idx_transactions_reference
    ON payments.transactions(reference_id);

CREATE TABLE IF NOT EXISTS payments.payouts (
    payout_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payee_user_id   UUID NOT NULL,
    payee_role      TEXT NOT NULL,
    amount          NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    status          TEXT NOT NULL DEFAULT 'PENDING',
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payout_period CHECK (period_end >= period_start)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payouts_payee_period
    ON payments.payouts(payee_user_id, payee_role, period_start, period_end);

CREATE TABLE IF NOT EXISTS payments.promotions (
    promotion_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id             UUID,
    code                    TEXT NOT NULL UNIQUE,
    discount_type           TEXT NOT NULL,
    discount_value          NUMERIC(12,2) NOT NULL CHECK (discount_value > 0),
    max_discount_amount     NUMERIC(12,2),
    min_order_value         NUMERIC(12,2),
    valid_from              TIMESTAMPTZ NOT NULL,
    valid_until             TIMESTAMPTZ NOT NULL,
    usage_limit_total       INTEGER,
    usage_limit_per_user    INTEGER,
    applicable_category     TEXT,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_promotion_window CHECK (valid_until > valid_from),
    CONSTRAINT chk_promotion_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FLAT'))
);

CREATE INDEX IF NOT EXISTS idx_promotions_provider
    ON payments.promotions(provider_id);

CREATE INDEX IF NOT EXISTS idx_promotions_active_window
    ON payments.promotions(is_active, valid_from, valid_until);

GRANT USAGE ON SCHEMA payments TO payment_service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payments TO payment_service_role;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payments TO payment_service_role;

DO $$
BEGIN
    IF to_regclass('captains.captain_earnings') IS NOT NULL THEN
        GRANT SELECT, UPDATE ON captains.captain_earnings TO payment_service_role;
    END IF;
    IF to_regclass('providers.providers') IS NOT NULL THEN
        GRANT SELECT ON providers.providers TO payment_service_role;
    END IF;
    IF to_regclass('orders.orders') IS NOT NULL THEN
        GRANT SELECT ON orders.orders TO payment_service_role;
    END IF;
    IF to_regclass('appointments.appointments') IS NOT NULL THEN
        GRANT SELECT ON appointments.appointments TO payment_service_role;
    END IF;
END $$;
