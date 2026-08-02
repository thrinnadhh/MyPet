CREATE TABLE IF NOT EXISTS orders.recurring_order_subscriptions (
    subscription_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    source_order_id UUID NOT NULL REFERENCES orders.orders(order_id),
    delivery_address_id UUID NOT NULL,
    cadence_days INTEGER NOT NULL,
    quantity_multiplier INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(40) NOT NULL,
    next_order_at TIMESTAMPTZ NOT NULL,
    last_reminded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_recurring_order_cadence CHECK (cadence_days IN (7, 15, 25, 30, 35)),
    CONSTRAINT ck_recurring_order_quantity CHECK (quantity_multiplier BETWEEN 1 AND 20),
    CONSTRAINT ck_recurring_order_status CHECK (status IN ('ACTIVE', 'PAUSED', 'AWAITING_CONFIRMATION', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_recurring_order_customer
    ON orders.recurring_order_subscriptions (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recurring_order_due
    ON orders.recurring_order_subscriptions (status, next_order_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_recurring_order_active_source
    ON orders.recurring_order_subscriptions (customer_id, source_order_id)
    WHERE status <> 'CANCELLED';
