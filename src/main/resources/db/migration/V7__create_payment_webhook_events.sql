CREATE TABLE payment_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL
);
