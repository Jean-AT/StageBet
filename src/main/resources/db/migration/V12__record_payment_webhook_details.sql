ALTER TABLE payment_webhook_events
    ADD COLUMN event_type VARCHAR(100),
    ADD COLUMN payment_reference VARCHAR(255);
