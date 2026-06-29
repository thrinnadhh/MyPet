-- =========================================================
-- PawsNearMe — Database Schema (DDL)
-- Target: Supabase (single project, schema-per-service)
-- =========================================================

-- ---------------------------------------------------------
-- SCHEMA: catalog
-- Owner service: Catalog/Inventory Service
-- Hybrid model: one Offering table serves both products
-- (DELIVERY) and services (APPOINTMENT) via nullable columns.
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS catalog;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'offering_status' AND typnamespace = 'catalog'::regnamespace) THEN
        CREATE TYPE catalog.offering_status AS ENUM ('ACTIVE', 'INACTIVE', 'OUT_OF_STOCK');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'slot_status' AND typnamespace = 'catalog'::regnamespace) THEN
        CREATE TYPE catalog.slot_status AS ENUM ('AVAILABLE', 'HELD', 'BOOKED', 'BLOCKED');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS catalog.offerings (
    offering_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id      UUID NOT NULL,            -- references providers.providers(provider_id), cross-schema, validated at app layer
    name             TEXT NOT NULL,
    description      TEXT,
    category         TEXT,                     -- 'FOOD', 'TOYS', 'CHECKUP', 'GROOMING_PACKAGE', etc.
    price            NUMERIC(10,2) NOT NULL,
    image_url        TEXT,
    status           catalog.offering_status NOT NULL DEFAULT 'ACTIVE',

    -- DELIVERY-only fields (NULL when fulfillment_type = APPOINTMENT)
    stock_quantity    INT,
    sku              TEXT,

    -- APPOINTMENT-only fields (NULL when fulfillment_type = DELIVERY)
    duration_minutes  INT,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_offering_has_correct_fields CHECK (
        (stock_quantity IS NOT NULL AND duration_minutes IS NULL) OR
        (stock_quantity IS NULL AND duration_minutes IS NOT NULL)
    )
);
CREATE INDEX IF NOT EXISTS idx_offerings_provider ON catalog.offerings(provider_id);
CREATE INDEX IF NOT EXISTS idx_offerings_category ON catalog.offerings(category);
CREATE INDEX IF NOT EXISTS idx_offerings_status ON catalog.offerings(status);

CREATE TABLE IF NOT EXISTS catalog.slots (
    slot_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offering_id      UUID NOT NULL REFERENCES catalog.offerings(offering_id) ON DELETE CASCADE,
    slot_start        TIMESTAMPTZ NOT NULL,
    slot_end          TIMESTAMPTZ NOT NULL,
    status           catalog.slot_status NOT NULL DEFAULT 'AVAILABLE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_slot_time_valid CHECK (slot_end > slot_start)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_slots_offering_time ON catalog.slots(offering_id, slot_start);
CREATE INDEX IF NOT EXISTS idx_slots_status ON catalog.slots(status);
