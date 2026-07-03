CREATE SCHEMA IF NOT EXISTS reviews;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'review_target_type') THEN
        CREATE TYPE reviews.review_target_type AS ENUM ('ORDER', 'APPOINTMENT', 'PROVIDER');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS reviews.reviews (
    review_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL,
    provider_id     UUID NOT NULL,
    target_type     reviews.review_target_type NOT NULL,
    target_id       UUID NOT NULL,
    rating          INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    captain_rating  INTEGER CHECK (captain_rating IS NULL OR captain_rating BETWEEN 1 AND 5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_target
    ON reviews.reviews(target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_reviews_provider
    ON reviews.reviews(provider_id);

CREATE INDEX IF NOT EXISTS idx_reviews_customer
    ON reviews.reviews(customer_id);
