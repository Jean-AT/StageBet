# WAGER-050 — Reliable messaging and settlement

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** WAGER-020, WAGER-030, WAGER-040

## Subtasks

- [ ] Add transactional outbox for committed domain changes.
- [ ] Publish versioned Kafka events with correlation and causation IDs.
- [ ] Implement idempotent consumers and an inbox/processed-event record where needed.
- [ ] Implement settlement for wins, losses, refunds, cancellation, and replay.
- [ ] Add bounded retries with backoff and jitter.
- [ ] Add dead-letter topics and a controlled replay command.
- [ ] Document at-least-once delivery and exactly-once business effect.

## Acceptance criteria

- If Kafka is unavailable after a database commit, the outbox record remains and is eventually published.
- Duplicate events do not double-settle a round or double-credit a wallet.
- Poison messages reach a DLQ with enough metadata to diagnose and replay them.
- Settlement is auditable and reconcilable against bets and ledger entries.
