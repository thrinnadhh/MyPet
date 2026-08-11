-- =========================================================
-- Schema: catalog (Sprint 24 — Categories, Variants & Featured)
-- =========================================================

CREATE TABLE IF NOT EXISTS catalog.categories (
    category_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             TEXT NOT NULL UNIQUE,
    name             TEXT NOT NULL,
    pet_type         TEXT NOT NULL DEFAULT 'ALL',  -- 'DOG', 'CAT', 'BIRD', 'FISH', 'SMALL_ANIMAL', 'ALL'
    parent_id        UUID REFERENCES catalog.categories(category_id) ON DELETE SET NULL,
    image_url        TEXT,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_categories_slug ON catalog.categories(slug);
CREATE INDEX IF NOT EXISTS idx_categories_parent ON catalog.categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_pet_type ON catalog.categories(pet_type);

CREATE TABLE IF NOT EXISTS catalog.offering_variants (
    variant_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offering_id      UUID NOT NULL REFERENCES catalog.offerings(offering_id) ON DELETE CASCADE,
    name             TEXT NOT NULL,                -- '500g', '1kg', '3kg', 'Salmon', etc.
    price            NUMERIC(10,2) NOT NULL,
    stock_quantity   INT NOT NULL DEFAULT 0,
    sku              TEXT,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_offering_variants_offering ON catalog.offering_variants(offering_id);

ALTER TABLE catalog.offerings
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS life_stage TEXT,
    ADD COLUMN IF NOT EXISTS product_type TEXT;

CREATE TABLE IF NOT EXISTS catalog.featured_collections (
    collection_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            TEXT NOT NULL,
    slug             TEXT NOT NULL UNIQUE,
    description      TEXT,
    image_url        TEXT,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS catalog.featured_collection_items (
    collection_id    UUID NOT NULL REFERENCES catalog.featured_collections(collection_id) ON DELETE CASCADE,
    offering_id      UUID NOT NULL REFERENCES catalog.offerings(offering_id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, offering_id)
);
