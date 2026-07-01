-- =========================================================
-- PawsNearMe — Database Schema (DDL)
-- Target: Supabase (single project, schema-per-service)
-- =========================================================

-- ---------------------------------------------------------
-- SCHEMA: identity
-- Owner service: Supabase Auth (extended profile data only)
-- Note: Supabase Auth owns auth.users; this schema holds
-- app-specific profile + role data, FK'd to auth.users.id
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS identity;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role' AND typnamespace = 'identity'::regnamespace) THEN
        CREATE TYPE identity.user_role AS ENUM ('CUSTOMER', 'MERCHANT', 'CAPTAIN', 'ADMIN');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS identity.profiles (
    user_id         UUID PRIMARY KEY,
    role            identity.user_role NOT NULL,
    full_name       TEXT NOT NULL,
    phone_number    TEXT NOT NULL UNIQUE,
    avatar_url      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_profiles_role ON identity.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_phone ON identity.profiles(phone_number);

CREATE TABLE IF NOT EXISTS identity.user_roles (
    user_id         UUID NOT NULL,
    role            identity.user_role NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE IF NOT EXISTS identity.pets (
    pet_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL,
    name            TEXT NOT NULL,
    species         TEXT NOT NULL,           -- 'DOG', 'CAT', 'BIRD', etc.
    breed           TEXT,
    date_of_birth   DATE,
    weight_kg       NUMERIC(5,2),
    photo_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pets_owner ON identity.pets(owner_id);

CREATE TABLE IF NOT EXISTS identity.addresses (
    address_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    label           TEXT,                    -- 'Home', 'Work', etc.
    line1           TEXT NOT NULL,
    line2           TEXT,
    city            TEXT NOT NULL,
    state           TEXT NOT NULL,
    pincode         TEXT NOT NULL,
    geo_lat         NUMERIC(9,6) NOT NULL,
    geo_lng         NUMERIC(9,6) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_addresses_user ON identity.addresses(user_id);


-- ---------------------------------------------------------
-- SCHEMA: providers
-- Owner service: Provider Service
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS providers;
CREATE EXTENSION IF NOT EXISTS postgis;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'provider_type' AND typnamespace = 'providers'::regnamespace) THEN
        CREATE TYPE providers.provider_type AS ENUM ('PET_STORE', 'VET_HOSPITAL', 'GROOMING_CENTER');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'fulfillment_type' AND typnamespace = 'providers'::regnamespace) THEN
        CREATE TYPE providers.fulfillment_type AS ENUM ('DELIVERY', 'APPOINTMENT');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'provider_status' AND typnamespace = 'providers'::regnamespace) THEN
        CREATE TYPE providers.provider_status AS ENUM ('DRAFT', 'PENDING_APPROVAL', 'INFO_REQUESTED', 'ACTIVE', 'SUSPENDED', 'REJECTED');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS providers.providers (
    provider_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id        UUID NOT NULL,               -- references auth.users(id), no FK across schema boundary by convention; validated at app layer
    provider_type        providers.provider_type NOT NULL,
    fulfillment_type      providers.fulfillment_type NOT NULL,
    name                 TEXT NOT NULL,
    description          TEXT,
    license_number        TEXT,                       -- required at app layer when provider_type = VET_HOSPITAL
    license_doc_url       TEXT,                       -- Supabase Storage reference
    address_line          TEXT NOT NULL,
    city                 TEXT NOT NULL,
    pincode              TEXT NOT NULL,
    geo_location          GEOGRAPHY(POINT, 4326) NOT NULL,   -- PostGIS point (lng, lat)
    status               providers.provider_status NOT NULL DEFAULT 'DRAFT',
    rating_avg            NUMERIC(3,2) DEFAULT 0.00,
    rating_count          INT DEFAULT 0,
    commission_pct         NUMERIC(5,2) NOT NULL DEFAULT 15.00,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_fulfillment_matches_type CHECK (
        (provider_type = 'PET_STORE' AND fulfillment_type = 'DELIVERY') OR
        (provider_type IN ('VET_HOSPITAL', 'GROOMING_CENTER') AND fulfillment_type = 'APPOINTMENT')
    )
);
CREATE INDEX IF NOT EXISTS idx_providers_geo ON providers.providers USING GIST(geo_location);
CREATE INDEX IF NOT EXISTS idx_providers_type_status ON providers.providers(provider_type, status);
CREATE INDEX IF NOT EXISTS idx_providers_owner ON providers.providers(owner_user_id);

-- Documents required during onboarding review (supports the
-- INFO_REQUESTED loop in the onboarding state machine)
CREATE TABLE IF NOT EXISTS providers.provider_documents (
    document_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id     UUID NOT NULL REFERENCES providers.providers(provider_id) ON DELETE CASCADE,
    doc_type        TEXT NOT NULL,            -- 'GST_CERTIFICATE', 'VET_LICENSE', 'SHOP_PROOF', etc.
    doc_url         TEXT NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed        BOOLEAN NOT NULL DEFAULT false,
    review_note     TEXT
);
CREATE INDEX IF NOT EXISTS idx_provider_docs_provider ON providers.provider_documents(provider_id);
