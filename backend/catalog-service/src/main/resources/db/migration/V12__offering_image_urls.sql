ALTER TABLE catalog.offerings
ADD COLUMN IF NOT EXISTS image_urls TEXT[] DEFAULT '{}';
