CREATE SCHEMA IF NOT EXISTS notifications;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'reminder_delivery_status') THEN
        CREATE TYPE notifications.reminder_delivery_status AS ENUM (
            'SCHEDULED',
            'ATTEMPTED',
            'DELIVERED',
            'DELIVERED_LOGGED',
            'FAILED'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS notifications.scheduled_reminders (
    reminder_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    reference_type      TEXT NOT NULL,
    reference_id        UUID NOT NULL,
    fire_at             TIMESTAMPTZ NOT NULL,
    fired               BOOLEAN NOT NULL DEFAULT FALSE,
    template_code       TEXT NOT NULL,
    delivery_status     notifications.reminder_delivery_status NOT NULL DEFAULT 'SCHEDULED',
    provider            TEXT,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    retryable_failure   BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reminders_fire_at
    ON notifications.scheduled_reminders(fire_at)
    WHERE fired = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_reminders_reference_template
    ON notifications.scheduled_reminders(reference_id, template_code);
