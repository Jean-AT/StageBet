ALTER TABLE idempotency_records
    ADD COLUMN result_reference_id UUID;

CREATE INDEX idempotency_records_result_reference_idx
    ON idempotency_records (result_reference_id);
