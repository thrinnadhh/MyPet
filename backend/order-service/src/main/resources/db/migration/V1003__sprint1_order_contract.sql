-- Sprint 1: canonical order lifecycle and optimistic transition protection.

UPDATE orders.orders
SET status = 'ASSIGNED'
WHERE status = 'REASSIGNED';

UPDATE orders.order_status_history
SET from_status = 'ASSIGNED'
WHERE from_status = 'REASSIGNED';

UPDATE orders.order_status_history
SET to_status = 'ASSIGNED'
WHERE to_status = 'REASSIGNED';

ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Keep legacy rows within the canonical order payment-status contract.
UPDATE orders.orders
SET payment_status = CASE
    WHEN UPPER(payment_status) = 'PARTIALLY_REFUNDED' THEN 'REFUNDED'
    WHEN UPPER(payment_status) = 'NOT_STARTED' AND UPPER(payment_method) = 'COD' THEN 'COD_PENDING'
    WHEN UPPER(payment_status) = 'NOT_STARTED' THEN 'PENDING'
    ELSE UPPER(payment_status)
END
WHERE payment_status IS NOT NULL;
