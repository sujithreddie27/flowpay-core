-- Users indexes
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_kyc_status ON users (kyc_status);
CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_users_email_status ON users (email, status);

-- Accounts indexes
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_accounts_status ON accounts (status);
CREATE INDEX idx_accounts_user_id_status ON accounts (user_id, status);
CREATE INDEX idx_accounts_created_at ON accounts (created_at);

-- Transactions indexes
CREATE INDEX idx_transactions_sender_id ON transactions (sender_id);
CREATE INDEX idx_transactions_receiver_id ON transactions (receiver_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
CREATE INDEX idx_transactions_sender_id_status ON transactions (sender_id, status);
CREATE INDEX idx_transactions_receiver_id_status ON transactions (receiver_id, status);
CREATE INDEX idx_transactions_sender_id_created_at ON transactions (sender_id, created_at DESC);
CREATE INDEX idx_transactions_receiver_id_created_at ON transactions (receiver_id, created_at DESC);
CREATE INDEX idx_transactions_processed_at ON transactions (processed_at);

-- Payment methods indexes
CREATE INDEX idx_payment_methods_user_id ON payment_methods (user_id);
CREATE INDEX idx_payment_methods_user_id_is_default ON payment_methods (user_id, is_default);
CREATE INDEX idx_payment_methods_status ON payment_methods (status);

-- Audit logs indexes
CREATE INDEX idx_audit_logs_entity_type_entity_id ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs (performed_by);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_entity_type_created_at ON audit_logs (entity_type, created_at DESC);
