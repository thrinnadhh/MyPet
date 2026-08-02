CREATE TABLE IF NOT EXISTS orders.admin_audit_logs (
    audit_id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(160),
    previous_value TEXT,
    new_value TEXT,
    reason VARCHAR(500) NOT NULL,
    trace_id VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at
    ON orders.admin_audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_entity
    ON orders.admin_audit_logs (entity_type, entity_id);

CREATE TABLE IF NOT EXISTS orders.service_area_configs (
    pincode VARCHAR(6) PRIMARY KEY,
    city VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    service_radius_km NUMERIC(6,2) NOT NULL,
    emergency_message VARCHAR(500),
    updated_by_user_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_service_area_pincode CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT ck_service_area_radius CHECK (service_radius_km >= 0.50 AND service_radius_km <= 100.00)
);

CREATE INDEX IF NOT EXISTS idx_service_area_configs_city
    ON orders.service_area_configs (city, pincode);
