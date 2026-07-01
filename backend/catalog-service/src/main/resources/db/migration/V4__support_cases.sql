CREATE TABLE IF NOT EXISTS orders.support_cases (
    support_case_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              TEXT NOT NULL,
    detail             TEXT NOT NULL,
    action_type        TEXT NOT NULL,
    entity_type        TEXT,
    entity_id          UUID,
    status             TEXT NOT NULL DEFAULT 'OPEN',
    created_by_user_id UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at        TIMESTAMPTZ,
    resolution_notes   TEXT
);

CREATE INDEX IF NOT EXISTS idx_support_cases_status ON orders.support_cases (status);
CREATE INDEX IF NOT EXISTS idx_support_cases_action_type ON orders.support_cases (action_type);
CREATE INDEX IF NOT EXISTS idx_support_cases_created_at ON orders.support_cases (created_at DESC);
