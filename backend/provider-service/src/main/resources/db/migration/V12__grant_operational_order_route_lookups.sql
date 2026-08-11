-- Sprint 3 operational projections require narrowly scoped read-only identity access.
-- OrderService reads the customer display name for merchant order detail.
-- DispatchService reads the order's saved delivery address only after dispatch ownership.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'order_service_role') THEN
        GRANT USAGE ON SCHEMA identity TO order_service_role;
        GRANT SELECT ON identity.profiles TO order_service_role;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'dispatch_service_role') THEN
        GRANT USAGE ON SCHEMA identity TO dispatch_service_role;
        GRANT SELECT ON identity.addresses TO dispatch_service_role;
    END IF;
END
$$;
