CREATE TABLE payment_methods (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    provider            VARCHAR(50)     NOT NULL,
    display_name        VARCHAR(100),
    tokenized_details   VARCHAR(500)    NOT NULL,
    last_four           VARCHAR(4),
    expiry_month        SMALLINT,
    expiry_year         SMALLINT,
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_verified         BOOLEAN         NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_methods_type CHECK (type IN ('CARD', 'BANK_ACCOUNT', 'UPI', 'WALLET')),
    CONSTRAINT chk_payment_methods_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'REVOKED'))
);
