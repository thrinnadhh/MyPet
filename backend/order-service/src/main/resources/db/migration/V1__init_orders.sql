CREATE SCHEMA IF NOT EXISTS orders;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS orders.orders (
    order_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID NOT NULL,
    provider_id          UUID NOT NULL,
    captain_id           UUID,
    delivery_address_id  UUID NOT NULL,
    status               VARCHAR(255) NOT NULL DEFAULT 'PLACED',
    subtotal_amount      NUMERIC(12,2) NOT NULL,
    delivery_fee         NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    discount_amount      NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_amount         NUMERIC(12,2) NOT NULL,
    payment_id           UUID,
    placed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at          TIMESTAMPTZ,
    ready_at             TIMESTAMPTZ,
    picked_up_at         TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  TEXT
);

CREATE TABLE IF NOT EXISTS orders.order_items (
    order_item_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL REFERENCES orders.orders(order_id) ON DELETE CASCADE,
    offering_id          UUID NOT NULL,
    offering_name_snapshot TEXT NOT NULL,
    unit_price_snapshot  NUMERIC(12,2) NOT NULL,
    quantity             INT NOT NULL CHECK (quantity > 0),
    line_total           NUMERIC(12,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders.order_status_history (
    history_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL REFERENCES orders.orders(order_id) ON DELETE CASCADE,
    from_status          VARCHAR(255),
    to_status            VARCHAR(255) NOT NULL,
    changed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_user_id   UUID,
    note                 TEXT
);

CREATE TABLE IF NOT EXISTS orders.system_configs (
    config_key           VARCHAR(255) PRIMARY KEY,
    config_value         TEXT NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS orders.disputes (
    dispute_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL,
    status               VARCHAR(255) NOT NULL DEFAULT 'OPEN',
    reason               TEXT NOT NULL,
    resolution_notes     TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at          TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS orders.support_cases (
    support_case_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                TEXT NOT NULL,
    detail               TEXT NOT NULL,
    action_type          VARCHAR(255) NOT NULL,
    entity_type          VARCHAR(255),
    entity_id            UUID,
    status               VARCHAR(255) NOT NULL DEFAULT 'OPEN',
    created_by_user_id   UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at          TIMESTAMPTZ,
    resolution_notes     TEXT
);

CREATE TABLE IF NOT EXISTS orders.invoices (
    invoice_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL UNIQUE REFERENCES orders.orders(order_id) ON DELETE CASCADE,
    invoice_number       VARCHAR(255) NOT NULL UNIQUE,
    subtotal_amount      NUMERIC(12,2) NOT NULL,
    tax_amount           NUMERIC(12,2) NOT NULL,
    total_amount         NUMERIC(12,2) NOT NULL,
    generated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders.orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_provider ON orders.orders(provider_id);
CREATE INDEX IF NOT EXISTS idx_orders_captain ON orders.orders(captain_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders.orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_placed_at ON orders.orders(placed_at);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON orders.order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_history_order ON orders.order_status_history(order_id);
