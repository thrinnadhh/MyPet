DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_service_role') THEN
        GRANT USAGE ON SCHEMA dispatch TO order_service_role;
        GRANT SELECT (job_id, order_id, status, created_at, resolved_at)
            ON TABLE dispatch.dispatch_jobs TO order_service_role;
        GRANT SELECT (job_id, captain_id, responded_at, response)
            ON TABLE dispatch.dispatch_offers TO order_service_role;
    END IF;
END
$$;
