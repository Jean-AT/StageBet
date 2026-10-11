# WAGER-030 — Crash game engine

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** WAGER-020

## Subtasks

- [ ] Define round lifecycle: scheduled, open, running, crashed, settled, cancelled.
- [ ] Generate and publish a verifiable crash point using a documented seed/hash scheme.
- [ ] Implement authoritative server-side round clock and monotonic event sequence.
- [ ] Broadcast multiplier updates over WebSocket without treating the client as authoritative.
- [ ] Persist round state needed for restart and audit.
- [ ] Define behavior for late bets, reconnects, clock drift, and server restart.

## Acceptance criteria

- A client cannot alter the crash point or settlement result.
- A round can be recovered after a process restart without creating a second settlement.
- The same seed and algorithm reproduce the documented result.
- The UI stream is an observation of server state, not the source of truth.
