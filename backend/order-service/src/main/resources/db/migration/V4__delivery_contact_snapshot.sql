ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS delivery_contact_phone VARCHAR(13),
    ADD COLUMN IF NOT EXISTS delivery_contact_verified BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_orders_delivery_contact_phone'
          AND conrelid = 'orders.orders'::regclass
    ) THEN
        ALTER TABLE orders.orders
            ADD CONSTRAINT chk_orders_delivery_contact_phone
            CHECK (
                delivery_contact_phone IS NULL
                OR delivery_contact_phone ~ '^[+]91[6-9][0-9]{9}$'
            );
    END IF;
END $$;
