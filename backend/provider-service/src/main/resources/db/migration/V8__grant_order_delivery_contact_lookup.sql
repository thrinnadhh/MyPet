-- Order creation snapshots the delivery contact from the authenticated customer's
-- saved address. Keep this cross-schema access read-only and table-specific.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'order_service_role') THEN
        GRANT USAGE ON SCHEMA identity TO order_service_role;
        GRANT SELECT ON identity.addresses TO order_service_role;
        GRANT SELECT ON identity.delivery_contacts TO order_service_role;
    END IF;
END
$$;
