-- Migration V5: Add pickup_otp and delivery_otp columns to dispatch_jobs if they do not exist
ALTER TABLE dispatch.dispatch_jobs ADD COLUMN IF NOT EXISTS pickup_otp VARCHAR(255);
ALTER TABLE dispatch.dispatch_jobs ADD COLUMN IF NOT EXISTS delivery_otp VARCHAR(255);
