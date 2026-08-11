ALTER TABLE providers.providers
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS opens_at TIME,
    ADD COLUMN IF NOT EXISTS closes_at TIME,
    ADD COLUMN IF NOT EXISTS weekly_off_days VARCHAR(80);

ALTER TABLE providers.providers
    DROP CONSTRAINT IF EXISTS chk_provider_contact_phone_format;

ALTER TABLE providers.providers
    ADD CONSTRAINT chk_provider_contact_phone_format
    CHECK (contact_phone IS NULL OR contact_phone ~ '^\+?[1-9][0-9]{7,14}$');

ALTER TABLE providers.providers
    DROP CONSTRAINT IF EXISTS chk_provider_operating_hours_pair;

ALTER TABLE providers.providers
    ADD CONSTRAINT chk_provider_operating_hours_pair
    CHECK ((opens_at IS NULL AND closes_at IS NULL) OR (opens_at IS NOT NULL AND closes_at IS NOT NULL));
