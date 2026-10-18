CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    player_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_intents_amount_check CHECK (amount > 0),
    CONSTRAINT payment_intents_currency_check CHECK (currency = upper(currency)),
    CONSTRAINT payment_intents_idempotency_key_unique UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT payment_intents_provider_reference_unique UNIQUE (provider, provider_reference)
);

CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    payment_intent_id UUID NOT NULL REFERENCES payment_intents (id),
    provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_refunds_amount_check CHECK (amount > 0),
    CONSTRAINT payment_refunds_currency_check CHECK (currency = upper(currency)),
    CONSTRAINT payment_refunds_idempotency_key_unique UNIQUE (tenant_id, payment_intent_id, idempotency_key),
    CONSTRAINT payment_refunds_provider_reference_unique UNIQUE (provider, provider_reference)
);
