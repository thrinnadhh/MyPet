ALTER TABLE identity.profiles
    ADD COLUMN IF NOT EXISTS preferred_locale TEXT NOT NULL DEFAULT 'en';

CREATE TABLE IF NOT EXISTS identity.vaccination_reminders (
    reminder_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id              UUID NOT NULL,
    owner_id            UUID NOT NULL,
    vaccine_name        TEXT NOT NULL,
    due_date            DATE NOT NULL,
    clinic_name         TEXT,
    enabled             BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vaccination_reminders_owner ON identity.vaccination_reminders(owner_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE identity.vaccination_reminders TO provider_service_role;
