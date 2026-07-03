CREATE SCHEMA IF NOT EXISTS dispatch;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS dispatch.dispatch_jobs (
    job_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL,
    status           VARCHAR(255) NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
    attempt_count    INT NOT NULL DEFAULT 0,
    max_attempts     INT NOT NULL DEFAULT 3,
    pickup_otp       VARCHAR(255),
    delivery_otp     VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS dispatch.dispatch_offers (
    offer_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id            UUID NOT NULL REFERENCES dispatch.dispatch_jobs(job_id) ON DELETE CASCADE,
    captain_id        UUID NOT NULL,
    offered_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at      TIMESTAMPTZ,
    response          TEXT,
    offer_rank        INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dispatch_jobs_order ON dispatch.dispatch_jobs(order_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_jobs_status ON dispatch.dispatch_jobs(status);
CREATE INDEX IF NOT EXISTS idx_dispatch_offers_job ON dispatch.dispatch_offers(job_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_offers_captain ON dispatch.dispatch_offers(captain_id);
