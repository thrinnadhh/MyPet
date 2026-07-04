ALTER TABLE content.promo_banners
    ADD COLUMN IF NOT EXISTS provider_id UUID,
    ADD COLUMN IF NOT EXISTS bid_amount DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING_BID';

CREATE TABLE IF NOT EXISTS content.banner_bids (
    bid_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id     UUID NOT NULL,
    owner_user_id   UUID NOT NULL,
    slot_order      INT NOT NULL CHECK (slot_order BETWEEN 0 AND 4),
    bid_amount      DECIMAL(10, 2) NOT NULL CHECK (bid_amount > 0),
    window_ends_at  TIMESTAMPTZ NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_banner_bids_window ON content.banner_bids (window_ends_at, status);
CREATE INDEX IF NOT EXISTS idx_banner_bids_owner ON content.banner_bids (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_banner_bids_slot_window ON content.banner_bids (slot_order, window_ends_at, status);

UPDATE content.promo_banners SET status = 'ACTIVE' WHERE active = true AND status = 'PENDING_BID';
