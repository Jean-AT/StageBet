# WAGER-040 — Betting and cash-out workflows

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** WAGER-020, WAGER-030

## Subtasks

- [ ] Add command to place a bet during the open phase.
- [ ] Reserve bet amount before accepting the bet.
- [ ] Add cash-out command with idempotency and round sequence validation.
- [ ] Resolve cash-out versus crash races deterministically on the server.
- [ ] Reject duplicate bets, late bets, invalid amounts, and unaffordable bets.
- [ ] Publish domain events for accepted bets, cash-outs, and crashed rounds.

## Acceptance criteria

- A valid bet is reserved exactly once.
- A cash-out accepted before the crash receives the correct payout.
- A cash-out that loses the race with the crash is rejected or settled as a loss according to the documented rule.
- Duplicate HTTP requests return a stable result instead of creating duplicate reservations or payouts.
