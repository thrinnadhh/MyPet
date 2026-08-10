-- Sprint 2: checkout idempotency and loyalty reconciliation persistence.

ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS checkout_request_id UUID,
    ADD COLUMN IF NOT EXISTS loyalty_reward_id UUID,
    ADD COLUMN IF NOT EXISTS loyalty_discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00;

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_checkout_request_id
    ON orders.orders(checkout_request_id)
    WHERE checkout_request_id IS NOT NULL;

ALTER TABLE orders.orders
    DROP CONSTRAINT IF EXISTS chk_orders_loyalty_discount_nonnegative;
ALTER TABLE orders.orders
    ADD CONSTRAINT chk_orders_loyalty_discount_nonnegative
    CHECK (loyalty_discount_amount >= 0);
