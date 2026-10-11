CREATE TABLE bets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    player_id UUID NOT NULL,
    round_id UUID NOT NULL REFERENCES crash_rounds (id),
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    hold_id UUID NOT NULL UNIQUE REFERENCES wallet_holds (id),
    accepted_sequence BIGINT NOT NULL,
    cash_out_sequence BIGINT,
    cash_out_multiplier NUMERIC(10, 2),
    payout_amount NUMERIC(19, 2),
    idempotency_key VARCHAR(128) NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cashed_out_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    CONSTRAINT bets_amount_check CHECK (amount > 0),
    CONSTRAINT bets_status_check CHECK (status IN ('ACCEPTED', 'CASHED_OUT', 'LOST', 'CANCELLED')),
    CONSTRAINT bets_currency_check CHECK (currency = upper(currency)),
    CONSTRAINT bets_tenant_idempotency_unique UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE bet_cash_outs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bet_id UUID NOT NULL REFERENCES bets (id),
    idempotency_key VARCHAR(128) NOT NULL,
    round_sequence BIGINT NOT NULL,
    multiplier NUMERIC(10, 2) NOT NULL,
    payout_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bet_cash_outs_idempotency_unique UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT bet_cash_outs_bet_unique UNIQUE (bet_id)
);

CREATE INDEX bets_round_status_idx ON bets (round_id, status);
