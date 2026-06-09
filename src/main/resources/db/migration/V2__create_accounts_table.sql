CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    account_number  VARCHAR(20)     NOT NULL,
    balance         DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    account_type    VARCHAR(20)     NOT NULL DEFAULT 'SAVINGS',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    daily_limit     DECIMAL(19, 4)  NOT NULL DEFAULT 10000.0000,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'WALLET')),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);
