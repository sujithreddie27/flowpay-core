-- Processed events table for consumer deduplication
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID PRIMARY KEY,
    topic           VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    consumer_group  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_processed_events_topic ON processed_events(topic);
CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
