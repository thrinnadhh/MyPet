CREATE TABLE IF NOT EXISTS catalog.internal_stock_mutations (
    idempotency_key UUID PRIMARY KEY,
    offering_id UUID NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('DECREMENT', 'RESTORE')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_internal_stock_mutations_offering
    ON catalog.internal_stock_mutations(offering_id, created_at DESC);
