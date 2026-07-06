-- Webhook Configurations
CREATE TABLE webhook_configs (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID            NOT NULL,
    url                 VARCHAR(2048)   NOT NULL,
    secret              VARCHAR(128)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    description         VARCHAR(500),
    failure_count       INTEGER         NOT NULL DEFAULT 0,
    last_triggered_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_webhook_configs_merchant FOREIGN KEY (merchant_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_webhook_configs_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_webhook_configs_merchant_id ON webhook_configs (merchant_id);
CREATE INDEX idx_webhook_configs_status ON webhook_configs (status);

-- Webhook Config Events (element collection)
CREATE TABLE webhook_config_events (
    webhook_config_id   UUID            NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,

    CONSTRAINT fk_webhook_config_events_config FOREIGN KEY (webhook_config_id) REFERENCES webhook_configs (id) ON DELETE CASCADE,
    CONSTRAINT chk_webhook_event_type CHECK (event_type IN ('PAYMENT_COMPLETED', 'PAYMENT_FAILED', 'PAYMENT_INITIATED', 'PAYMENT_REVERSED', 'REFUND_COMPLETED', 'ACCOUNT_UPDATED'))
);

CREATE INDEX idx_webhook_config_events_config_id ON webhook_config_events (webhook_config_id);

-- Webhook Deliveries
CREATE TABLE webhook_deliveries (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_config_id   UUID            NOT NULL,
    transaction_id      UUID,
    event_type          VARCHAR(50)     NOT NULL,
    url                 VARCHAR(2048)   NOT NULL,
    request_body        TEXT,
    response_body       TEXT,
    http_status         INTEGER,
    attempt_count       INTEGER         NOT NULL DEFAULT 0,
    max_attempts        INTEGER         NOT NULL DEFAULT 5,
    successful          BOOLEAN         NOT NULL DEFAULT FALSE,
    failure_reason      VARCHAR(1000),
    next_retry_at       TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_webhook_delivery_config FOREIGN KEY (webhook_config_id) REFERENCES webhook_configs (id) ON DELETE CASCADE
);

CREATE INDEX idx_webhook_deliveries_config_id ON webhook_deliveries (webhook_config_id);
CREATE INDEX idx_webhook_deliveries_transaction_id ON webhook_deliveries (transaction_id);
CREATE INDEX idx_webhook_deliveries_successful ON webhook_deliveries (successful);
CREATE INDEX idx_webhook_deliveries_next_retry ON webhook_deliveries (next_retry_at);

-- Notifications
CREATE TABLE notifications (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    subject             VARCHAR(500)    NOT NULL,
    content             TEXT            NOT NULL,
    recipient_email     VARCHAR(255),
    transaction_id      UUID,
    retry_count         INTEGER         NOT NULL DEFAULT 0,
    failure_reason      VARCHAR(1000),
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type CHECK (type IN ('EMAIL', 'WEBHOOK', 'SMS', 'PUSH')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'RETRYING'))
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_status ON notifications (status);
CREATE INDEX idx_notifications_type ON notifications (type);
CREATE INDEX idx_notifications_transaction_id ON notifications (transaction_id);

-- Notification Preferences
CREATE TABLE notification_preferences (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL UNIQUE,
    email_enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    sms_enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    push_enabled        BOOLEAN         NOT NULL DEFAULT TRUE,
    webhook_enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_notification_prefs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_notification_prefs_user_id ON notification_preferences (user_id);

-- Notification Preference Events (element collection)
CREATE TABLE notification_preference_events (
    preference_id       UUID            NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,

    CONSTRAINT fk_notification_pref_events_pref FOREIGN KEY (preference_id) REFERENCES notification_preferences (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_pref_events_pref_id ON notification_preference_events (preference_id);
