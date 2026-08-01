-- Create database login roles before canonical service V1 migrations run.
-- Some V1 migrations grant privileges directly to their service role.
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
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'content_service_role') THEN
        CREATE ROLE content_service_role WITH LOGIN PASSWORD 'content_service_pass';
    END IF;
END
$$;
