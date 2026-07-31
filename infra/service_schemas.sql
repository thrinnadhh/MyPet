-- Schemas owned by service-local Flyway migrations must exist before
-- db_roles.sql grants schema privileges during fresh database bootstrap.
CREATE SCHEMA IF NOT EXISTS chat;
CREATE SCHEMA IF NOT EXISTS content;
