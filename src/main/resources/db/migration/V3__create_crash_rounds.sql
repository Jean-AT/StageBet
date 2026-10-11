CREATE TABLE crash_rounds (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    server_seed TEXT NOT NULL,
    server_seed_hash CHAR(64) NOT NULL,
    crash_multiplier NUMERIC(10, 2) NOT NULL,
    last_multiplier NUMERIC(10, 2) NOT NULL,
    event_sequence BIGINT NOT NULL DEFAULT 0,
    scheduled_at TIMESTAMPTZ NOT NULL,
    opened_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    crashed_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT crash_rounds_status_check CHECK (status IN ('SCHEDULED', 'OPEN', 'RUNNING', 'CRASHED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT crash_rounds_multiplier_check CHECK (crash_multiplier >= 1.01),
    CONSTRAINT crash_rounds_last_multiplier_check CHECK (last_multiplier >= 1.00),
    CONSTRAINT crash_rounds_sequence_check CHECK (event_sequence >= 0)
);

CREATE INDEX crash_rounds_status_idx ON crash_rounds (status, scheduled_at);
