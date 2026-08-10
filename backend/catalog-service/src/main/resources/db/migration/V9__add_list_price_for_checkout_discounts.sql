ALTER TABLE catalog.offerings
    ADD COLUMN IF NOT EXISTS list_price NUMERIC(10,2);

-- Existing catalog rows have no separate list/MRP value. Backfill to the
-- current selling price so they preserve today's zero item-discount behavior.
UPDATE catalog.offerings
SET list_price = price
WHERE list_price IS NULL;
