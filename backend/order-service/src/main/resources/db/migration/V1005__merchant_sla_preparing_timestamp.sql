ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS preparing_at TIMESTAMPTZ;

-- Backfill from the canonical history so existing PREPARING-or-later orders
-- preserve the time the merchant actually started preparation.
UPDATE orders.orders o
SET preparing_at = h.preparing_at
FROM (
    SELECT order_id, MIN(changed_at) AS preparing_at
    FROM orders.order_status_history
    WHERE to_status = 'PREPARING'
    GROUP BY order_id
) h
WHERE o.order_id = h.order_id
  AND o.preparing_at IS NULL;