-- Sprint 2: authoritative item-level discount source.
ALTER TABLE catalog.offerings
    ADD COLUMN IF NOT EXISTS list_price NUMERIC(12,2);

UPDATE catalog.offerings
SET list_price = price
WHERE list_price IS NULL;

ALTER TABLE catalog.offerings
    DROP CONSTRAINT IF EXISTS chk_catalog_list_price_not_below_price;
ALTER TABLE catalog.offerings
    ADD CONSTRAINT chk_catalog_list_price_not_below_price
    CHECK (list_price IS NULL OR list_price >= price);
