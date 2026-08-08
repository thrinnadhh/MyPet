ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS recurring_occurrence_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_recurring_occurrence
    ON orders.orders (recurring_occurrence_id)
    WHERE recurring_occurrence_id IS NOT NULL;

ALTER TABLE orders.recurring_order_subscriptions
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'COD',
    ADD COLUMN IF NOT EXISTS last_executed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_order_id UUID REFERENCES orders.orders(order_id),
    ADD COLUMN IF NOT EXISTS last_failure_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS last_failure_detail TEXT;

CREATE TABLE IF NOT EXISTS orders.recurring_order_occurrences (
    occurrence_id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES orders.recurring_order_subscriptions(subscription_id) ON DELETE CASCADE,
    scheduled_for TIMESTAMPTZ NOT NULL,
    order_id UUID REFERENCES orders.orders(order_id),
    status VARCHAR(40) NOT NULL,
    failure_code VARCHAR(80),
    failure_detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recurring_order_occurrence UNIQUE (subscription_id, scheduled_for),
    CONSTRAINT ck_recurring_order_occurrence_status CHECK (status IN ('PROCESSING', 'ORDER_CREATED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_recurring_order_occurrence_subscription
    ON orders.recurring_order_occurrences (subscription_id, scheduled_for DESC);
CREATE INDEX IF NOT EXISTS idx_recurring_order_occurrence_order
    ON orders.recurring_order_occurrences (order_id)
    WHERE order_id IS NOT NULL;
