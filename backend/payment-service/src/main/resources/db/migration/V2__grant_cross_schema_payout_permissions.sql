DO $$
BEGIN
    IF to_regclass('captains.captain_earnings') IS NOT NULL THEN
        GRANT SELECT, UPDATE ON captains.captain_earnings TO payment_service_role;
    END IF;
    IF to_regclass('captains.captain_profiles') IS NOT NULL THEN
        GRANT SELECT ON captains.captain_profiles TO payment_service_role;
    END IF;
    IF to_regclass('providers.providers') IS NOT NULL THEN
        GRANT SELECT ON providers.providers TO payment_service_role;
    END IF;
    IF to_regclass('orders.orders') IS NOT NULL THEN
        GRANT SELECT ON orders.orders TO payment_service_role;
    END IF;
    IF to_regclass('appointments.appointments') IS NOT NULL THEN
        GRANT SELECT ON appointments.appointments TO payment_service_role;
    END IF;
END $$;
