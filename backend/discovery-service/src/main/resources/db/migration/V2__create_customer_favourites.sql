CREATE SCHEMA IF NOT EXISTS customer;

CREATE TABLE IF NOT EXISTS customer.favourites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_favourite UNIQUE (customer_id, target_type, target_id)
);
