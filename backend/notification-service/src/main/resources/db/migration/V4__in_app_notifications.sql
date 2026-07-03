CREATE TABLE IF NOT EXISTS notifications.in_app_notifications (
    notification_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    notification_type TEXT NOT NULL,
    title             TEXT NOT NULL,
    body              TEXT NOT NULL,
    reference_id      UUID,
    priority          TEXT NOT NULL DEFAULT 'NORMAL',
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_user ON notifications.in_app_notifications(user_id, read_at);
