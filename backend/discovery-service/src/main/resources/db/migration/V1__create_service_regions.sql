CREATE TABLE IF NOT EXISTS providers.service_regions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city_identity VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    state VARCHAR(128) NOT NULL,
    country VARCHAR(128) NOT NULL DEFAULT 'India',
    center_latitude DOUBLE PRECISION NOT NULL,
    center_longitude DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL DEFAULT 25.0,
    pincodes TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 1,
    allow_products BOOLEAN NOT NULL DEFAULT true,
    allow_grooming BOOLEAN NOT NULL DEFAULT true,
    allow_vet BOOLEAN NOT NULL DEFAULT true,
    allow_own_delivery BOOLEAN NOT NULL DEFAULT true,
    allow_3p_delivery BOOLEAN NOT NULL DEFAULT true,
    allow_cod BOOLEAN NOT NULL DEFAULT true,
    allow_online_payment BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO providers.service_regions (
    id, city_identity, display_name, state, country, center_latitude, center_longitude, radius_km, pincodes, status, sort_order
) VALUES (
    '81111111-1111-1111-1111-111111111111', 'tirupati', 'Tirupati', 'Andhra Pradesh', 'India', 13.6288, 79.4192, 25.0, '517501,517502,517507', 'ENABLED', 1
) ON CONFLICT (city_identity) DO NOTHING;
