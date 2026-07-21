-- Performance optimization indexes for high-throughput transaction processing

-- Composite indexes for transaction history queries (covering indexes)
CREATE INDEX IF NOT EXISTS idx_transactions_sender_status_created
    ON transactions (sender_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_receiver_status_created
    ON transactions (receiver_id, status, created_at DESC);

-- Partial index for active/pending transactions (reduces index size)
CREATE INDEX IF NOT EXISTS idx_transactions_pending
    ON transactions (created_at, sender_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_transactions_processing
    ON transactions (created_at, sender_id)
    WHERE status = 'PROCESSING';

-- Index for daily limit calculation (frequently queried aggregate)
CREATE INDEX IF NOT EXISTS idx_transactions_sender_amount_date
    ON transactions (sender_id, created_at, amount)
    WHERE status IN ('COMPLETED', 'PROCESSING', 'PENDING');

-- Composite index for idempotency checks (high-frequency lookup)
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key_status
    ON transactions (idempotency_key, status)
    WHERE idempotency_key IS NOT NULL;

-- Accounts: Index for active accounts balance lookups
CREATE INDEX IF NOT EXISTS idx_accounts_user_currency_active
    ON accounts (user_id, currency)
    WHERE status = 'ACTIVE';

-- Partial index for retryable failed transactions
CREATE INDEX IF NOT EXISTS idx_transactions_retryable
    ON transactions (retry_count, updated_at)
    WHERE status = 'FAILED' AND retry_count < 3;

-- Index for stale pending transaction cleanup
CREATE INDEX IF NOT EXISTS idx_transactions_stale_pending
    ON transactions (updated_at)
    WHERE status = 'PENDING';

-- BRIN index for time-series audit log queries (very space efficient)
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at_brin
    ON audit_logs USING BRIN (created_at);

-- Optimize payment_methods lookup by user with active status
CREATE INDEX IF NOT EXISTS idx_payment_methods_user_active
    ON payment_methods (user_id, type)
    WHERE status = 'ACTIVE';
