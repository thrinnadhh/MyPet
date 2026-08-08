ALTER TABLE catalog.offerings
    ADD COLUMN IF NOT EXISTS admin_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS moderated_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_catalog_offerings_public_eligibility
    ON catalog.offerings(provider_id, status, admin_disabled);

CREATE TABLE IF NOT EXISTS catalog.moderation_audit_logs (
    audit_id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    offering_id UUID NOT NULL REFERENCES catalog.offerings(offering_id),
    action VARCHAR(80) NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    new_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_catalog_moderation_audit_offering_created
    ON catalog.moderation_audit_logs(offering_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_catalog_moderation_audit_actor_created
    ON catalog.moderation_audit_logs(admin_user_id, created_at DESC);
