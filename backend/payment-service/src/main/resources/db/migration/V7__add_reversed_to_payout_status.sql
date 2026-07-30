-- Older environments used an enum while the current schema stores payout
-- status as TEXT. Keep the migration compatible with both layouts.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'payments'
          AND t.typname = 'payout_status'
    ) THEN
        EXECUTE 'ALTER TYPE payments.payout_status ADD VALUE IF NOT EXISTS ''REVERSED''';
    END IF;
END $$;
