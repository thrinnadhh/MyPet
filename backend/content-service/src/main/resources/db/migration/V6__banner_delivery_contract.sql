ALTER TABLE content.promo_banners
    ADD COLUMN IF NOT EXISTS image_url TEXT,
    ADD COLUMN IF NOT EXISTS target_type TEXT NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS target_value TEXT,
    ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ;

ALTER TABLE content.promo_banners
    DROP CONSTRAINT IF EXISTS ck_promo_banner_target_type;
ALTER TABLE content.promo_banners
    ADD CONSTRAINT ck_promo_banner_target_type
    CHECK (target_type IN ('NONE', 'PRODUCT', 'STORE', 'CATEGORY', 'ROUTE'));

ALTER TABLE content.promo_banners
    DROP CONSTRAINT IF EXISTS ck_promo_banner_window;
ALTER TABLE content.promo_banners
    ADD CONSTRAINT ck_promo_banner_window
    CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at);

CREATE INDEX IF NOT EXISTS idx_promo_banners_delivery
    ON content.promo_banners(active, status, sort_order, starts_at, ends_at);
