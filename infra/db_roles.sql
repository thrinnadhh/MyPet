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
END
$$;

-- Allow connections to pawsnearme database
GRANT CONNECT ON DATABASE pawsnearme TO identity_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO provider_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO catalog_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO discovery_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO order_service_role;
GRANT CONNECT ON DATABASE pawsnearme TO appointment_service_role;

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
