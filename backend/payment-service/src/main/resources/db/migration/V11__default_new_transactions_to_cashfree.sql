ALTER TABLE payments.transactions
    ALTER COLUMN gateway SET DEFAULT 'CASHFREE';

COMMENT ON COLUMN payments.transactions.gateway IS
    'Payment provider for this transaction. New transactions default to CASHFREE; legacy RAZORPAY rows remain auditable.';
