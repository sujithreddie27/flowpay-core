CREATE TABLE audit_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       UUID            NOT NULL,
    action          VARCHAR(30)     NOT NULL,
    old_value       JSONB,
    new_value       JSONB,
    performed_by    UUID,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_logs_performed_by FOREIGN KEY (performed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'STATUS_CHANGE', 'TRANSFER', 'VERIFICATION'))
);
