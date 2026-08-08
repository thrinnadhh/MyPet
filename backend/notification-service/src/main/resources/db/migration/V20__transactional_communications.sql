CREATE TABLE IF NOT EXISTS notifications.notification_contacts (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320),
    display_name VARCHAR(200),
    phone VARCHAR(32),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_contacts_email
    ON notifications.notification_contacts (LOWER(email))
    WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS notifications.notification_reference_owners (
    reference_type VARCHAR(40) NOT NULL,
    reference_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (reference_type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_reference_owners_user
    ON notifications.notification_reference_owners (user_id);

CREATE TABLE IF NOT EXISTS notifications.email_deliveries (
    email_delivery_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    user_id UUID,
    recipient_email VARCHAR(320) NOT NULL,
    recipient_name VARCHAR(200),
    template_code VARCHAR(80) NOT NULL,
    variables_json TEXT NOT NULL,
    provider VARCHAR(30),
    provider_message_id VARCHAR(255),
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_email_deliveries_retry
    ON notifications.email_deliveries (status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX IF NOT EXISTS idx_email_deliveries_msg91_monthly
    ON notifications.email_deliveries (provider, status, sent_at)
    WHERE provider = 'MSG91' AND status = 'SENT';
