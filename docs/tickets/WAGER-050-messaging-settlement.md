# WAGER-050 — Reliable messaging and settlement

**Parent:** WAGER-000  
**Status:** In Progress
**Dependencies:** WAGER-020, WAGER-030, WAGER-040

## Subtasks

- [x] Add transactional outbox for committed domain changes.
- [x] Publish versioned Kafka events with correlation and causation IDs.
- [x] Add an inbox/processed-event record for idempotent consumers.
- [x] Implement settlement for wins, losses, refunds, cancellation, and replay.
- [x] Add bounded retries with backoff and jitter.
- [x] Add dead-letter topics and a controlled replay command.
- [x] Document at-least-once delivery and exactly-once business effect.

Implementation note: Kafka delivery is at-least-once; the outbox uniqueness constraint,
wallet idempotency keys, bet state transitions, and inbox primary key provide one
business effect. A Kafka listener integration remains a follow-up once event consumers
are introduced.

## Acceptance criteria

- If Kafka is unavailable after a database commit, the outbox record remains and is eventually published.
- Duplicate events do not double-settle a round or double-credit a wallet.
- Poison messages reach a DLQ with enough metadata to diagnose and replay them.
- Settlement is auditable and reconcilable against bets and ledger entries.
