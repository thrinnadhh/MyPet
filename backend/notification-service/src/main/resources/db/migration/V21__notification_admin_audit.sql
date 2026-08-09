CREATE TABLE IF NOT EXISTS notifications.notification_admin_audit (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID NOT NULL,
    previous_state VARCHAR(80),
    new_state VARCHAR(80),
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_admin_audit_created_at
    ON notifications.notification_admin_audit (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_admin_audit_target
    ON notifications.notification_admin_audit (target_type, target_id, created_at DESC);

-- Audit evidence is append-only at the database layer for the normal application role.
-- The migration owner remains able to perform schema maintenance, but runtime writes
-- only INSERT rows and exposes no update/delete repository operation.
