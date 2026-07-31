-- The base captain schema predates the onboarding documents and bank fields.
-- Service Flyway uses baseline-on-migrate, so its V1 migration is skipped when
-- the shared base schema already exists. Keep the fresh-database bootstrap
-- equivalent to that migration with idempotent DDL.
CREATE TABLE IF NOT EXISTS captains.captain_documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id UUID NOT NULL,
    doc_type TEXT NOT NULL,
    doc_url TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_captain_documents_captain
    ON captains.captain_documents(captain_id);

ALTER TABLE captains.captain_profiles
    ADD COLUMN IF NOT EXISTS bank_account TEXT,
    ADD COLUMN IF NOT EXISTS bank_ifsc TEXT,
    ADD COLUMN IF NOT EXISTS selfie_doc_url TEXT;
