ALTER TABLE reviews.reviews
ADD COLUMN IF NOT EXISTS images TEXT[] DEFAULT '{}',
ADD COLUMN IF NOT EXISTS is_verified_purchase BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN IF NOT EXISTS offering_id UUID;

CREATE INDEX IF NOT EXISTS idx_reviews_offering ON reviews.reviews(offering_id);
