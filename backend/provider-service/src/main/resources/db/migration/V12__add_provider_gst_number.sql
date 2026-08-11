ALTER TABLE providers.providers
ADD COLUMN IF NOT EXISTS gst_number TEXT;
