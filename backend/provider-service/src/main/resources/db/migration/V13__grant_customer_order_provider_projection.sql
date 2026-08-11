DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_service_role') THEN
        GRANT USAGE ON SCHEMA providers TO order_service_role;
        GRANT SELECT (provider_id, name, provider_type, geo_location)
            ON TABLE providers.providers TO order_service_role;
    END IF;
END
$$;
