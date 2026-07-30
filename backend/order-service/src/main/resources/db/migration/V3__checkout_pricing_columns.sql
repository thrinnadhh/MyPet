ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS coupon_code TEXT,
    ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'CARD',
    ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'PENDING';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_orders_tax_nonnegative'
          AND conrelid = 'orders.orders'::regclass
    ) THEN
        ALTER TABLE orders.orders
            ADD CONSTRAINT chk_orders_tax_nonnegative CHECK (tax_amount >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_orders_payment_method'
          AND conrelid = 'orders.orders'::regclass
    ) THEN
        ALTER TABLE orders.orders
            ADD CONSTRAINT chk_orders_payment_method
            CHECK (payment_method IN ('CARD', 'UPI', 'COD'));
    END IF;
END $$;
