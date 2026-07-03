CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_reference_active
    ON payments.transactions(reference_id)
    WHERE status IN ('PENDING', 'SUCCESS');
