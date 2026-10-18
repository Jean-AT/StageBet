ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claim_token UUID;

UPDATE outbox_events
SET status = 'PENDING',
    next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'IN_FLIGHT';

CREATE INDEX outbox_events_stale_claim_idx
    ON outbox_events (claimed_at, created_at)
    WHERE status = 'IN_FLIGHT';
