-- Create roles for each microservice
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_service_role') THEN
        CREATE ROLE identity_service_role WITH LOGIN PASSWORD 'identity_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'provider_service_role') THEN
        CREATE ROLE provider_service_role WITH LOGIN PASSWORD 'provider_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'catalog_service_role') THEN
        CREATE ROLE catalog_service_role WITH LOGIN PASSWORD 'catalog_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'discovery_service_role') THEN
        CREATE ROLE discovery_service_role WITH LOGIN PASSWORD 'discovery_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'order_service_role') THEN
        CREATE ROLE order_service_role WITH LOGIN PASSWORD 'order_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'appointment_service_role') THEN
        CREATE ROLE appointment_service_role WITH LOGIN PASSWORD 'appointment_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'dispatch_service_role') THEN
        CREATE ROLE dispatch_service_role WITH LOGIN PASSWORD 'dispatch_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'captain_service_role') THEN
        CREATE ROLE captain_service_role WITH LOGIN PASSWORD 'captain_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'notification_service_role') THEN
        CREATE ROLE notification_service_role WITH LOGIN PASSWORD 'notification_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'review_service_role') THEN
        CREATE ROLE review_service_role WITH LOGIN PASSWORD 'review_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'payment_service_role') THEN
        CREATE ROLE payment_service_role WITH LOGIN PASSWORD 'payment_service_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'chat_service_role') THEN
        CREATE ROLE chat_service_role WITH LOGIN PASSWORD 'chat_service_pass';
    END IF;
END
$$;

-- Allow connections to pawsnearme database
GRANT CONNECT ON DATABASE pawsnearme TO identity_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO provider_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO catalog_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO discovery_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO order_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO appointment_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO dispatch_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO captain_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO notification_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO review_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO payment_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO chat_service_role;

-- Scoping for Identity Service
GRANT USAGE ON SCHEMA identity TO identity_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA identity TO identity_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA identity TO identity_service_role;

-- Scoping for Provider Onboarding / Provider Service
GRANT USAGE ON SCHEMA providers TO provider_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA providers TO provider_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA providers TO provider_service_role;

-- Provider Service needs SELECT access on identity.profiles to validate owner exists
GRANT USAGE ON SCHEMA identity TO provider_service_role;
GRANT SELECT ON identity.profiles TO provider_service_role;

-- Scoping for Catalog Service
GRANT USAGE ON SCHEMA catalog TO catalog_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA catalog TO catalog_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA catalog TO catalog_service_role;
-- Catalog Service needs SELECT access on providers.providers to validate provider exists
GRANT USAGE ON SCHEMA providers TO catalog_service_role;
GRANT SELECT ON providers.providers TO catalog_service_role;

-- Scoping for Discovery Service
GRANT USAGE ON SCHEMA providers TO discovery_service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA providers TO discovery_service_role;
GRANT USAGE ON SCHEMA catalog TO discovery_service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA catalog TO discovery_service_role;

-- Scoping for Order Service
GRANT USAGE ON SCHEMA orders TO order_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA orders TO order_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA orders TO order_service_role;
GRANT USAGE ON SCHEMA providers TO order_service_role;
GRANT SELECT ON providers.providers TO order_service_role;
GRANT USAGE ON SCHEMA catalog TO order_service_role;
GRANT SELECT, UPDATE ON catalog.offerings TO order_service_role;

-- Scoping for Appointment Service
GRANT USAGE ON SCHEMA appointments TO appointment_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA appointments TO appointment_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA appointments TO appointment_service_role;
GRANT USAGE ON SCHEMA catalog TO appointment_service_role;
GRANT SELECT, UPDATE ON catalog.slots TO appointment_service_role;
GRANT SELECT ON catalog.offerings TO appointment_service_role;
GRANT USAGE ON SCHEMA providers TO appointment_service_role;
GRANT SELECT ON providers.providers TO appointment_service_role;

-- Scoping for Dispatch Service
GRANT USAGE ON SCHEMA dispatch TO dispatch_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA dispatch TO dispatch_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA dispatch TO dispatch_service_role;
GRANT USAGE ON SCHEMA orders TO dispatch_service_role;
GRANT SELECT, UPDATE ON orders.orders TO dispatch_service_role;
GRANT SELECT ON orders.order_items TO dispatch_service_role;
GRANT USAGE ON SCHEMA providers TO dispatch_service_role;
GRANT SELECT ON providers.providers TO dispatch_service_role;

-- Scoping for Captain Service
GRANT USAGE ON SCHEMA captains TO captain_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA captains TO captain_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA captains TO captain_service_role;
GRANT USAGE ON SCHEMA identity TO captain_service_role;
GRANT SELECT ON identity.profiles TO captain_service_role;
GRANT SELECT ON identity.user_roles TO captain_service_role;
GRANT USAGE ON SCHEMA orders TO captain_service_role;
GRANT SELECT ON orders.orders TO captain_service_role;

-- Scoping for Notification Service
GRANT USAGE ON SCHEMA notifications TO notification_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA notifications TO notification_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA notifications TO notification_service_role;

-- Scoping for Review Service
GRANT USAGE ON SCHEMA reviews TO review_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA reviews TO review_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA reviews TO review_service_role;

-- Scoping for Payment Service
GRANT USAGE ON SCHEMA payments TO payment_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA payments TO payment_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA payments TO payment_service_role;

-- Payout aggregation read access
GRANT USAGE ON SCHEMA orders TO payment_service_role;
GRANT SELECT ON orders.orders TO payment_service_role;
GRANT SELECT ON orders.order_items TO payment_service_role;

GRANT USAGE ON SCHEMA appointments TO payment_service_role;
GRANT SELECT ON appointments.appointments TO payment_service_role;

GRANT USAGE ON SCHEMA captains TO payment_service_role;
GRANT SELECT ON captains.captain_profiles TO payment_service_role;
GRANT SELECT ON captains.captain_earnings TO payment_service_role;

GRANT USAGE ON SCHEMA providers TO payment_service_role;
GRANT SELECT ON providers.providers TO payment_service_role;

-- Scoping for Chat Service
GRANT USAGE ON SCHEMA chat TO chat_service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA chat TO chat_service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA chat TO chat_service_role;
GRANT USAGE ON SCHEMA identity TO chat_service_role;
GRANT SELECT ON identity.profiles TO chat_service_role;
GRANT USAGE ON SCHEMA providers TO chat_service_role;
GRANT SELECT ON providers.providers TO chat_service_role;
