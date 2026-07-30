CREATE TABLE IF NOT EXISTS payments.coupon_reservations (
    reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id   UUID NOT NULL REFERENCES payments.promotions(promotion_id),
    code           TEXT NOT NULL,
    user_id        UUID NOT NULL,
    order_id       UUID,
    discount_amount NUMERIC(12,2) NOT NULL CHECK (discount_amount >= 0),
    status         TEXT NOT NULL DEFAULT 'HELD'
        CHECK (status IN ('HELD', 'REDEEMED', 'RELEASED', 'EXPIRED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '15 minutes')
);

CREATE INDEX IF NOT EXISTS idx_coupon_reservations_promotion_status
    ON payments.coupon_reservations (promotion_id, status);

CREATE INDEX IF NOT EXISTS idx_coupon_reservations_user_status
    ON payments.coupon_reservations (promotion_id, user_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_coupon_reservations_order_active
    ON payments.coupon_reservations (order_id)
    WHERE order_id IS NOT NULL AND status IN ('HELD', 'REDEEMED');

CREATE TABLE IF NOT EXISTS payments.cod_configs (
    config_key   TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO payments.cod_configs (config_key, config_value)
VALUES
    ('global_max_amount', '1000.00'),
    ('city_overrides_json', '{}'),
    ('disabled_cities_json', '[]')
ON CONFLICT (config_key) DO NOTHING;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON payments.coupon_reservations, payments.cod_configs
    TO payment_service_role;
