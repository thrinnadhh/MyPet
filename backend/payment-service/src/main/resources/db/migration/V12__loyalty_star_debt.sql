CREATE TABLE IF NOT EXISTS payments.loyalty_star_debts (
    debt_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL,
    provider_id  UUID NOT NULL,
    debt_stars   INTEGER NOT NULL DEFAULT 0 CHECK (debt_stars >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_loyalty_star_debt_customer_provider UNIQUE (customer_id, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_loyalty_star_debt_customer
    ON payments.loyalty_star_debts(customer_id, provider_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON payments.loyalty_star_debts TO payment_service_role;
