CREATE TABLE IF NOT EXISTS orders.customer_cases (
    case_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders.orders(order_id),
    customer_id UUID NOT NULL,
    case_type VARCHAR(60) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    refund_status VARCHAR(40) NOT NULL DEFAULT 'NOT_APPLICABLE',
    resolution_notes VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_customer_case_type CHECK (case_type IN ('MISSING_ITEM','DAMAGED_ITEM','WRONG_ITEM','LATE_DELIVERY','PAYMENT_ISSUE','OTHER')),
    CONSTRAINT ck_customer_case_status CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED','REJECTED')),
    CONSTRAINT ck_customer_case_refund CHECK (refund_status IN ('NOT_APPLICABLE','PENDING','PROCESSING','COMPLETED','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_customer_cases_customer
    ON orders.customer_cases (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_cases_status
    ON orders.customer_cases (status, created_at DESC);

CREATE TABLE IF NOT EXISTS orders.customer_case_evidence (
    evidence_id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES orders.customer_cases(case_id),
    uploader_user_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_customer_case_evidence_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

CREATE INDEX IF NOT EXISTS idx_customer_case_evidence_case
    ON orders.customer_case_evidence (case_id, created_at ASC);
