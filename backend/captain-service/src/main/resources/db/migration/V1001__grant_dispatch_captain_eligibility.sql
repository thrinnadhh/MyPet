-- Sprint 3 dispatch eligibility must verify persisted captain approval rather than
-- trusting Redis availability markers alone.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'dispatch_service_role') THEN
        GRANT USAGE ON SCHEMA captains TO dispatch_service_role;
        GRANT SELECT ON captains.captain_profiles TO dispatch_service_role;
    END IF;
END
$$;
