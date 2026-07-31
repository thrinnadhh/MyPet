-- Captain Service has no standalone base-schema V1: its V1 onboarding
-- migration assumes captains.captain_profiles already exists. Define the
-- current base profile/earnings schema and onboarding fields together.
CREATE SCHEMA IF NOT EXISTS captains;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type
        WHERE typname = 'captain_status'
          AND typnamespace = 'captains'::regnamespace
    ) THEN
        CREATE TYPE captains.captain_status AS ENUM (
            'PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'REJECTED'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_type
        WHERE typname = 'vehicle_type'
          AND typnamespace = 'captains'::regnamespace
    ) THEN
        CREATE TYPE captains.vehicle_type AS ENUM (
            'BIKE', 'SCOOTER', 'BICYCLE', 'ON_FOOT'
        );
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS captains.captain_profiles (
    captain_id       UUID PRIMARY KEY,
    status           captains.captain_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    vehicle_type     captains.vehicle_type NOT NULL,
    vehicle_number   TEXT,
    license_doc_url  TEXT,
    bank_account     TEXT,
    bank_ifsc        TEXT,
    selfie_doc_url   TEXT,
    rating_avg       NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    rating_count     INTEGER NOT NULL DEFAULT 0,
    total_deliveries INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_captain_profiles_status
    ON captains.captain_profiles(status);

CREATE TABLE IF NOT EXISTS captains.captain_earnings (
    earning_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id UUID NOT NULL REFERENCES captains.captain_profiles(captain_id) ON DELETE CASCADE,
    order_id   UUID NOT NULL,
    amount     NUMERIC(12,2) NOT NULL,
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    payout_id  UUID
);

CREATE INDEX IF NOT EXISTS idx_captain_earnings_captain
    ON captains.captain_earnings(captain_id);

CREATE INDEX IF NOT EXISTS idx_captain_earnings_payout
    ON captains.captain_earnings(payout_id);

CREATE TABLE IF NOT EXISTS captains.captain_documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id  UUID NOT NULL,
    doc_type    TEXT NOT NULL,
    doc_url     TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_captain_documents_captain
    ON captains.captain_documents(captain_id);
