CREATE TABLE IF NOT EXISTS payments.loyalty_programs (
    program_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         UUID,
    target_stars        INTEGER NOT NULL DEFAULT 10 CHECK (target_stars > 0),
    reward_amount       NUMERIC(12,2) NOT NULL DEFAULT 50.00 CHECK (reward_amount > 0),
    min_order_value     NUMERIC(12,2) NOT NULL DEFAULT 199.00 CHECK (min_order_value >= 0),
    welcome_star_policy BOOLEAN NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    is_stackable        BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_days         INTEGER NOT NULL DEFAULT 60 CHECK (expiry_days > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loyalty_program_provider
    ON payments.loyalty_programs(provider_id)
    WHERE provider_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_loyalty_program_platform_default
    ON payments.loyalty_programs((1))
    WHERE provider_id IS NULL;

CREATE TABLE IF NOT EXISTS payments.customer_loyalty_accounts (
    account_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID NOT NULL,
    provider_id          UUID NOT NULL,
    star_balance         INTEGER NOT NULL DEFAULT 0 CHECK (star_balance >= 0),
    cycle_count          INTEGER NOT NULL DEFAULT 0 CHECK (cycle_count >= 0),
    total_stars_earned   INTEGER NOT NULL DEFAULT 0 CHECK (total_stars_earned >= 0),
    total_rewards_issued INTEGER NOT NULL DEFAULT 0 CHECK (total_rewards_issued >= 0),
    welcome_star_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_customer_loyalty_provider UNIQUE (customer_id, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_loyalty_customer
    ON payments.customer_loyalty_accounts(customer_id);

CREATE TABLE IF NOT EXISTS payments.loyalty_ledger_entries (
    entry_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL,
    provider_id  UUID NOT NULL,
    delta_stars  INTEGER NOT NULL,
    entry_type   TEXT NOT NULL CHECK (entry_type IN (
        'WELCOME_STAR', 'PURCHASE_STAR', 'CYCLE_ROLLOVER',
        'STAR_REVERSAL', 'ADMIN_ADJUSTMENT'
    )),
    reference_id UUID,
    actor_id     UUID,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_loyalty_ledger_customer_provider
    ON payments.loyalty_ledger_entries(customer_id, provider_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_loyalty_ledger_reference
    ON payments.loyalty_ledger_entries(reference_id);

CREATE TABLE IF NOT EXISTS payments.loyalty_reward_instances (
    reward_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID NOT NULL,
    provider_id   UUID NOT NULL,
    reward_amount NUMERIC(12,2) NOT NULL CHECK (reward_amount > 0),
    status        TEXT NOT NULL DEFAULT 'ISSUED' CHECK (status IN (
        'ISSUED', 'RESERVED', 'REDEEMED', 'REVOKED', 'EXPIRED'
    )),
    code          TEXT NOT NULL UNIQUE,
    order_id      UUID,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_loyalty_rewards_customer_status
    ON payments.loyalty_reward_instances(customer_id, status);

CREATE INDEX IF NOT EXISTS idx_loyalty_rewards_provider_status
    ON payments.loyalty_reward_instances(customer_id, provider_id, status);

CREATE TABLE IF NOT EXISTS payments.loyalty_processed_events (
    processed_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type   TEXT NOT NULL,
    reference_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_loyalty_processed_event UNIQUE (event_type, reference_id)
);

CREATE TABLE IF NOT EXISTS payments.loyalty_audit_logs (
    audit_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID NOT NULL,
    provider_id UUID,
    action      TEXT NOT NULL,
    before_json TEXT,
    after_json  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_loyalty_audit_provider_created
    ON payments.loyalty_audit_logs(provider_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    payments.loyalty_programs,
    payments.customer_loyalty_accounts,
    payments.loyalty_ledger_entries,
    payments.loyalty_reward_instances,
    payments.loyalty_processed_events,
    payments.loyalty_audit_logs
TO payment_service_role;
