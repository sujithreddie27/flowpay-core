-- FlowPay Core - Database Initialization Script
-- This script creates initial database objects and configurations
-- Run after migrations are complete

-- Create indexes for better query performance (if not already in V6__add_indexes.sql)
-- These are additional indexes for common query patterns

-- Index for user email lookups (unique constraint should already create this)
-- CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Index for user status filtering
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Index for account lookups by user
CREATE INDEX IF NOT EXISTS idx_accounts_user_id_status ON accounts(user_id, status);

-- Index for transaction history queries
CREATE INDEX IF NOT EXISTS idx_transactions_sender_created ON transactions(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver_created ON transactions(receiver_id, created_at DESC);

-- Index for transaction status monitoring
CREATE INDEX IF NOT EXISTS idx_transactions_status_created ON transactions(status, created_at DESC);

-- Index for payment method lookups
CREATE INDEX IF NOT EXISTS idx_payment_methods_user_default ON payment_methods(user_id, is_default);

-- Index for audit log queries
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id, created_at DESC);

-- Create a function for updating the updated_at timestamp automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply the trigger to tables with updated_at column
DO $$
BEGIN
    -- Users table
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_users_updated_at') THEN
        CREATE TRIGGER update_users_updated_at
            BEFORE UPDATE ON users
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;

    -- Accounts table
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_accounts_updated_at') THEN
        CREATE TRIGGER update_accounts_updated_at
            BEFORE UPDATE ON accounts
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;

    -- Transactions table
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_transactions_updated_at') THEN
        CREATE TRIGGER update_transactions_updated_at
            BEFORE UPDATE ON transactions
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;

    -- Payment Methods table
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_payment_methods_updated_at') THEN
        CREATE TRIGGER update_payment_methods_updated_at
            BEFORE UPDATE ON payment_methods
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Create a view for transaction summaries (useful for reporting)
CREATE OR REPLACE VIEW transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM transactions
GROUP BY DATE(created_at), status;

-- Grant appropriate permissions (adjust based on your security requirements)
-- GRANT SELECT ON transaction_summary TO flowpay_readonly;

-- Insert any default/seed data if needed (for non-production environments)
-- This section can be expanded based on requirements

COMMENT ON TABLE users IS 'Stores user account information';
COMMENT ON TABLE accounts IS 'Stores financial accounts linked to users';
COMMENT ON TABLE transactions IS 'Records all payment transactions';
COMMENT ON TABLE payment_methods IS 'Stores payment method details for users';
COMMENT ON TABLE audit_logs IS 'Audit trail for all entity changes';
