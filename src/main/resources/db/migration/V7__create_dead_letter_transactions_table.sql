CREATE TABLE dead_letter_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    reference_id    VARCHAR(64) NOT NULL,
    original_status VARCHAR(20) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'UNRESOLVED',
    failure_reason  VARCHAR(1000) NOT NULL,
    exception_class VARCHAR(255),
    stack_trace     TEXT,
    retry_count     INT NOT NULL DEFAULT 0,
    max_retries_exhausted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata        JSONB,
    resolution_notes VARCHAR(1000),
    resolved_at     TIMESTAMP WITH TIME ZONE,
    resolved_by     UUID REFERENCES users(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dlt_transaction_id ON dead_letter_transactions(transaction_id);
CREATE INDEX idx_dlt_status ON dead_letter_transactions(status);
CREATE INDEX idx_dlt_created_at ON dead_letter_transactions(created_at);
