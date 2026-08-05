CREATE SCHEMA IF NOT EXISTS providers;

CREATE TABLE IF NOT EXISTS providers.city_launch_requests (
    request_id UUID PRIMARY KEY,
    city_name VARCHAR(120) NOT NULL,
    normalized_city VARCHAR(120) NOT NULL,
    contact_info VARCHAR(254) NOT NULL,
    normalized_contact VARCHAR(254) NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'CUSTOMER_APP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_city_launch_request UNIQUE (normalized_city, normalized_contact)
);

CREATE INDEX IF NOT EXISTS idx_city_launch_requests_city
    ON providers.city_launch_requests(normalized_city, created_at DESC);
