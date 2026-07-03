DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE t.typname = 'reminder_delivery_status'
          AND n.nspname = 'notifications'
    ) THEN
        CREATE TYPE notifications.reminder_delivery_status AS ENUM (
            'SCHEDULED',
            'ATTEMPTED',
            'DELIVERED',
            'DELIVERED_LOGGED',
            'FAILED'
        );
    END IF;
END $$;

ALTER TABLE notifications.scheduled_reminders
    ADD COLUMN IF NOT EXISTS delivery_status notifications.reminder_delivery_status NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN IF NOT EXISTS provider TEXT,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retryable_failure BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS ux_reminders_reference_template
    ON notifications.scheduled_reminders(reference_id, template_code);
