-- =========================================================
-- PawsNearMe — V2 Barcode & Billing Schema
-- Sprint 8: EAN/UPC barcode billing add-on
-- =========================================================

-- Add barcode column to catalog.offerings (unique per provider)
ALTER TABLE catalog.offerings
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS idx_offerings_provider_barcode
    ON catalog.offerings (provider_id, barcode)
    WHERE barcode IS NOT NULL;

-- ─── BILLING SCHEMA ────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE IF NOT EXISTS billing.bills (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id        UUID            NOT NULL,
    staff_id        UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT, FINALIZED, SYNCED
    subtotal        NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_discount  NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    tax             NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    grand_total     NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100)    NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    synced_at       TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS billing.bill_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id         UUID            NOT NULL REFERENCES billing.bills(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    barcode_scanned VARCHAR(100)    NOT NULL,
    quantity        INTEGER         NOT NULL,
    unit_price      NUMERIC(12, 2)  NOT NULL,
    discount_amount NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    discount_type   VARCHAR(10)     NOT NULL DEFAULT 'NONE'  -- NONE, FLAT, PERCENT
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_bills_store_id    ON billing.bills (store_id);
CREATE INDEX IF NOT EXISTS idx_bills_staff_id    ON billing.bills (staff_id);
CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id ON billing.bill_items (bill_id);
