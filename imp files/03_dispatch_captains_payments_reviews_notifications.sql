-- ---------------------------------------------------------
-- SCHEMA: dispatch
-- Owner service: Dispatch Service (DELIVERY fulfillment only)
-- Note: live Captain location/availability lives in Redis Geo,
-- NOT here. This schema is the durable job-assignment record.
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS dispatch;

CREATE TYPE dispatch.job_status AS ENUM (
    'PENDING_ASSIGNMENT', 'OFFERED', 'ACCEPTED', 'REJECTED', 'TIMED_OUT', 'COMPLETED', 'FAILED'
);

CREATE TABLE dispatch.dispatch_jobs (
    job_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL,             -- references orders.orders(order_id), cross-schema
    status           dispatch.job_status NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
    attempt_count      INT NOT NULL DEFAULT 0,
    max_attempts       INT NOT NULL DEFAULT 3,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMPTZ
);
CREATE INDEX idx_dispatch_jobs_order ON dispatch.dispatch_jobs(order_id);
CREATE INDEX idx_dispatch_jobs_status ON dispatch.dispatch_jobs(status);

-- One row per Captain offered the job — supports the 3-retry
-- reassignment loop from the Order state machine (Flows doc 2.1)
CREATE TABLE dispatch.dispatch_offers (
    offer_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id            UUID NOT NULL REFERENCES dispatch.dispatch_jobs(job_id) ON DELETE CASCADE,
    captain_id         UUID NOT NULL,           -- references auth.users(id), cross-schema
    offered_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at         TIMESTAMPTZ,
    response          TEXT,                     -- 'ACCEPTED', 'REJECTED', 'TIMED_OUT'
    offer_rank         INT NOT NULL              -- 1st choice, 2nd choice, etc.
);
CREATE INDEX idx_dispatch_offers_job ON dispatch.dispatch_offers(job_id);
CREATE INDEX idx_dispatch_offers_captain ON dispatch.dispatch_offers(captain_id);


-- ---------------------------------------------------------
-- SCHEMA: captains
-- Owner service: Captain Service
-- Durable Captain profile/vehicle/earnings data. Live
-- location/online-status is Redis-only (see PRD 6.1).
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS captains;

CREATE TYPE captains.captain_status AS ENUM ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'REJECTED');
CREATE TYPE captains.vehicle_type AS ENUM ('BIKE', 'SCOOTER', 'BICYCLE', 'ON_FOOT');

CREATE TABLE captains.captain_profiles (
    captain_id        UUID PRIMARY KEY,         -- references auth.users(id)
    status            captains.captain_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    vehicle_type        captains.vehicle_type NOT NULL,
    vehicle_number       TEXT,
    license_doc_url       TEXT,                  -- Supabase Storage reference
    rating_avg          NUMERIC(3,2) DEFAULT 0.00,
    rating_count         INT DEFAULT 0,
    total_deliveries      INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_captain_profiles_status ON captains.captain_profiles(status);

CREATE TABLE captains.captain_earnings (
    earning_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id         UUID NOT NULL REFERENCES captains.captain_profiles(captain_id) ON DELETE CASCADE,
    order_id          UUID NOT NULL,            -- references orders.orders(order_id), cross-schema
    amount            NUMERIC(10,2) NOT NULL,
    earned_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    payout_id          UUID                      -- references payments.payouts(payout_id), cross-schema, nullable until paid
);
CREATE INDEX idx_captain_earnings_captain ON captains.captain_earnings(captain_id);
CREATE INDEX idx_captain_earnings_payout ON captains.captain_earnings(payout_id);


-- ---------------------------------------------------------
-- SCHEMA: payments
-- Owner service: Payment Service
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TYPE payments.transaction_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED');
CREATE TYPE payments.transaction_type AS ENUM ('ORDER_PAYMENT', 'APPOINTMENT_PAYMENT', 'REFUND');
CREATE TYPE payments.payout_status AS ENUM ('PENDING', 'PROCESSING', 'PAID', 'REVERSED', 'FAILED');


CREATE TABLE payments.transactions (
    transaction_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,           -- references auth.users(id), cross-schema
    transaction_type      payments.transaction_type NOT NULL,
    reference_id        UUID NOT NULL,          -- order_id or appointment_id, cross-schema, polymorphic by transaction_type
    amount             NUMERIC(10,2) NOT NULL,
    currency           TEXT NOT NULL DEFAULT 'INR',
    status             payments.transaction_status NOT NULL DEFAULT 'PENDING',
    gateway            TEXT NOT NULL,            -- 'RAZORPAY', etc.
    gateway_transaction_id TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_user ON payments.transactions(user_id);
CREATE INDEX idx_transactions_reference ON payments.transactions(reference_id);
CREATE INDEX idx_transactions_status ON payments.transactions(status);

CREATE TABLE payments.payouts (
    payout_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payee_user_id        UUID NOT NULL,          -- references auth.users(id) — merchant or captain, cross-schema
    payee_role          identity.user_role NOT NULL,   -- cross-schema type reference
    amount             NUMERIC(10,2) NOT NULL,
    status             payments.payout_status NOT NULL DEFAULT 'PENDING',
    razorpay_transfer_id TEXT,
    period_start         DATE NOT NULL,
    period_end          DATE NOT NULL,
    paid_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payouts_payee ON payments.payouts(payee_user_id);
CREATE INDEX idx_payouts_status ON payments.payouts(status);

CREATE TABLE IF NOT EXISTS payments.linked_accounts (
    linked_account_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payee_user_id           UUID NOT NULL UNIQUE,
    payee_role             identity.user_role NOT NULL,
    account_number         TEXT NOT NULL,
    ifsc                   TEXT NOT NULL,
    business_name          TEXT NOT NULL,
    email                  TEXT NOT NULL,
    razorpay_account_id    TEXT NOT NULL,
    pending_clawback_balance NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_linked_accounts_payee ON payments.linked_accounts(payee_user_id);

CREATE TABLE IF NOT EXISTS payments.platform_commission_ledger (
    ledger_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id           UUID NOT NULL,
    order_id              UUID,
    gross_amount          NUMERIC(10,2) NOT NULL,
    commission_pct        NUMERIC(5,2) NOT NULL,
    commission_amount     NUMERIC(10,2) NOT NULL,
    net_merchant_amount   NUMERIC(10,2) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_commission_ledger_provider ON payments.platform_commission_ledger(provider_id);


-- Commission/discount governance — backs the Discount War
-- Prevention System referenced in the PRD (Section 6.2)
CREATE TABLE payments.promotions (
    promotion_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         UUID,                   -- references providers.providers(provider_id), cross-schema; NULL = platform-wide
    code              TEXT UNIQUE,
    discount_type        TEXT NOT NULL,           -- 'PERCENTAGE', 'FLAT'
    discount_value        NUMERIC(10,2) NOT NULL,
    max_discount_amount     NUMERIC(10,2),
    min_order_value       NUMERIC(10,2),
    valid_from          TIMESTAMPTZ NOT NULL,
    valid_until          TIMESTAMPTZ NOT NULL,
    usage_limit_total      INT,
    usage_limit_per_user     INT,
    applicable_category     TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promotions_provider ON payments.promotions(provider_id);
CREATE INDEX idx_promotions_active ON payments.promotions(is_active, valid_from, valid_until);


-- ---------------------------------------------------------
-- SCHEMA: reviews
-- Owner service: Review Service — shared across all provider types
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS reviews;

CREATE TYPE reviews.review_target_type AS ENUM ('ORDER', 'APPOINTMENT');

CREATE TABLE reviews.reviews (
    review_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL,          -- references auth.users(id), cross-schema
    provider_id         UUID NOT NULL,          -- references providers.providers(provider_id), cross-schema
    target_type         reviews.review_target_type NOT NULL,
    target_id          UUID NOT NULL,           -- order_id or appointment_id, polymorphic by target_type
    rating             SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment            TEXT,
    captain_rating        SMALLINT CHECK (captain_rating BETWEEN 1 AND 5),  -- only set for ORDER reviews
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_reviews_target ON reviews.reviews(target_type, target_id);
CREATE INDEX idx_reviews_provider ON reviews.reviews(provider_id);
CREATE INDEX idx_reviews_customer ON reviews.reviews(customer_id);


-- ---------------------------------------------------------
-- SCHEMA: notifications
-- Owner service: Notification Service
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TYPE notifications.channel AS ENUM ('PUSH', 'SMS', 'EMAIL');
CREATE TYPE notifications.delivery_status AS ENUM ('QUEUED', 'SENT', 'FAILED', 'DELIVERED');

CREATE TABLE notifications.notification_log (
    notification_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,          -- references auth.users(id), cross-schema
    channel            notifications.channel NOT NULL,
    template_code        TEXT NOT NULL,          -- maps to Notification Matrix doc
    payload            JSONB,
    status             notifications.delivery_status NOT NULL DEFAULT 'QUEUED',
    triggering_event_id     UUID,                -- correlates to Kafka event (see Event Schema Registry)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at            TIMESTAMPTZ
);
CREATE INDEX idx_notification_log_user ON notifications.notification_log(user_id);
CREATE INDEX idx_notification_log_status ON notifications.notification_log(status);

-- Scheduled reminders (T-24h / T-1h appointment reminders, etc.)
CREATE TABLE notifications.scheduled_reminders (
    reminder_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,
    reference_type        TEXT NOT NULL,          -- 'APPOINTMENT', 'ORDER'
    reference_id         UUID NOT NULL,
    fire_at            TIMESTAMPTZ NOT NULL,
    fired             BOOLEAN NOT NULL DEFAULT false,
    template_code        TEXT NOT NULL
);
CREATE INDEX idx_reminders_fire_at ON notifications.scheduled_reminders(fire_at) WHERE fired = false;

-- ---------------------------------------------------------
-- SCHEMA: providers / service_regions
-- Owner service: Discovery Service / Provider Service
-- Admin-controlled service cities, serviceability & flags
-- ---------------------------------------------------------
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


-- ---------------------------------------------------------
-- SCHEMA: providers / service_regions
-- Owner service: Discovery Service / Provider Service
-- Admin-controlled service cities, serviceability & flags
-- ---------------------------------------------------------
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

-- ---------------------------------------------------------
-- SCHEMA: customer / favourites
-- Owner service: Discovery Service / Customer Service
-- Tenant-safe user favourites (products & shop profiles)
-- ---------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS customer;

CREATE TABLE IF NOT EXISTS customer.favourites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    target_type VARCHAR(32) NOT NULL, -- 'PRODUCT' or 'SHOP'
    target_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_favourite UNIQUE (customer_id, target_type, target_id)
);
GRANT ALL ON SCHEMA customer TO public;
GRANT ALL ON TABLE customer.favourites TO public;


