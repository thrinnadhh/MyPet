CREATE TABLE IF NOT EXISTS notifications.device_push_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    expo_push_token VARCHAR(255) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    app_role VARCHAR(32),
    sound_profile VARCHAR(32) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, expo_push_token)
);

CREATE INDEX IF NOT EXISTS idx_device_push_tokens_user ON notifications.device_push_tokens(user_id);
