# WAGER-030 — Crash game engine

**Parent:** WAGER-000  
**Status:** Done
**Dependencies:** WAGER-020

## Subtasks

- [x] Define round lifecycle: scheduled, open, running, crashed, settled, cancelled.
- [x] Generate and publish a verifiable crash point using a documented seed/hash scheme.
- [x] Implement authoritative server-side round clock and monotonic event sequence.
- [x] Broadcast multiplier updates over WebSocket without treating the client as authoritative.
- [x] Persist round state needed for restart and audit.
- [x] Define behavior for late bets, reconnects, clock drift, and server restart.

## Implementation notes

- The server creates a cryptographically random seed and publishes its SHA-256 hash with round events.
- The crash point is derived reproducibly with `HMAC-SHA256(serverSeed, roundId)` and a documented house-edge formula.
- The server reveals the seed only after the round is crashed or settled; clients can then verify the published hash and crash point.
- Round timestamps, state, multiplier, event sequence, and version are persisted in PostgreSQL. The server clock drives multiplier ticks; clients only observe WebSocket events.
- Late bets must be rejected by the betting application layer unless the round is `OPEN`; reconnecting clients must reload the persisted snapshot and ignore events older than their last sequence.
- A restart recovers the persisted round and seed without regenerating the crash point. Settlement remains an explicit one-way `CRASHED` to `SETTLED` transition.

## Acceptance criteria

- A client cannot alter the crash point or settlement result.
- A round can be recovered after a process restart without creating a second settlement.
- The same seed and algorithm reproduce the documented result.
- The UI stream is an observation of server state, not the source of truth.

## Code review

- Domain transitions are framework-independent and reject invalid lifecycle changes, decreasing multipliers, and backwards event time.
- Persistence is restart-safe for a single application instance and settlement is idempotent at the state-transition boundary: a settled round cannot be settled again.
- Production hardening remains for a later ticket: database-level optimistic locking or `FOR UPDATE` should be added before running multiple engine instances, and WebSocket allowed origins should be restricted by environment.
