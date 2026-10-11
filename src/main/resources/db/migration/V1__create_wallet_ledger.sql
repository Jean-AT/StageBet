CREATE TABLE wallet_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    player_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    available_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    reserved_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wallet_accounts_currency_check CHECK (currency = upper(currency)),
    CONSTRAINT wallet_accounts_available_balance_check CHECK (available_balance >= 0),
    CONSTRAINT wallet_accounts_reserved_balance_check CHECK (reserved_balance >= 0),
    CONSTRAINT wallet_accounts_player_unique UNIQUE (tenant_id, player_id, currency)
);

CREATE TABLE wallet_holds (
    id UUID PRIMARY KEY,
    wallet_account_id UUID NOT NULL REFERENCES wallet_accounts (id),
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    CONSTRAINT wallet_holds_amount_check CHECK (amount > 0),
    CONSTRAINT wallet_holds_status_check CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT wallet_holds_reference_unique UNIQUE (wallet_account_id, reference_type, reference_id)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    actor_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ledger_transactions_idempotency_unique UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions (id),
    wallet_account_id UUID NOT NULL REFERENCES wallet_accounts (id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ledger_entries_type_check CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entries_amount_check CHECK (amount > 0)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT idempotency_records_key_unique UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX ledger_entries_wallet_created_idx
    ON ledger_entries (wallet_account_id, created_at);

CREATE INDEX wallet_holds_expiration_idx
    ON wallet_holds (status, expires_at);
