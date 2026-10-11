CREATE TABLE responsible_gaming_profiles (
    tenant_id UUID NOT NULL,
    player_id UUID NOT NULL,
    daily_bet_limit NUMERIC(19, 2),
    session_bet_limit NUMERIC(19, 2),
    daily_bet_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    session_bet_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    daily_total_date DATE NOT NULL DEFAULT CURRENT_DATE,
    session_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    self_excluded_until TIMESTAMPTZ,
    cool_off_until TIMESTAMPTZ,
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, player_id),
    CONSTRAINT responsible_gaming_daily_limit_check CHECK (daily_bet_limit IS NULL OR daily_bet_limit > 0),
    CONSTRAINT responsible_gaming_session_limit_check CHECK (session_bet_limit IS NULL OR session_bet_limit > 0),
    CONSTRAINT responsible_gaming_daily_total_check CHECK (daily_bet_total >= 0),
    CONSTRAINT responsible_gaming_session_total_check CHECK (session_bet_total >= 0)
);

CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    subject_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    details JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_records_subject_idx ON audit_records (tenant_id, subject_type, subject_id, created_at);

CREATE TABLE rate_limit_buckets (
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    bucket_name VARCHAR(100) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, subject_id, bucket_name),
    CONSTRAINT rate_limit_count_check CHECK (request_count >= 0)
);
