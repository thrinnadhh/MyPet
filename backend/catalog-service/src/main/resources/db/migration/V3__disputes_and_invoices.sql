-- =========================================================
-- PawsNearMe — V3 Disputes and Invoices
-- Sprint 9: Legal, support disputes, config, invoicing
-- =========================================================

-- Create system config table in orders schema
CREATE TABLE IF NOT EXISTS orders.system_configs (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Insert default dispute refund mode config ('MANUAL' vs 'AUTOMATED')
INSERT INTO orders.system_configs (config_key, config_value)
VALUES ('dispute_refund_mode', 'MANUAL')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value;

-- Create disputes table in orders schema
CREATE TABLE IF NOT EXISTS orders.disputes (
    dispute_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN, RESOLVED, REJECTED
    reason           TEXT NOT NULL,
    resolution_notes TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_disputes_order_id ON orders.disputes (order_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON orders.disputes (status);

-- Create GST-compliant invoices table in orders schema
CREATE TABLE IF NOT EXISTS orders.invoices (
    invoice_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID UNIQUE NOT NULL,
    invoice_number   VARCHAR(100) UNIQUE NOT NULL,
    subtotal_amount  NUMERIC(12, 2) NOT NULL CHECK (subtotal_amount >= 0),
    tax_amount       NUMERIC(12, 2) NOT NULL CHECK (tax_amount >= 0), -- 18% GST
    total_amount     NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoices_order_id ON orders.invoices (order_id);
