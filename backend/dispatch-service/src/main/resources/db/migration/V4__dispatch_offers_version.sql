-- Migration V4: Add version column to dispatch_offers for optimistic locking support
ALTER TABLE dispatch.dispatch_offers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
