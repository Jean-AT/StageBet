ALTER TABLE idempotency_records
    ADD COLUMN result_wallet_id UUID,
    ADD COLUMN result_available_balance NUMERIC(19, 2),
    ADD COLUMN result_reserved_balance NUMERIC(19, 2),
    ADD COLUMN result_currency CHAR(3);
