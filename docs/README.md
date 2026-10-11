# WagerCore project backlog

This folder is the local ticket system for the WagerCore portfolio project. Tickets are intentionally written as Markdown so they can be reviewed, versioned, and later copied into Linear, GitHub Issues, or another tracker.

## Ticket hierarchy

- [WAGER-000 — Build WagerCore iGaming Platform](tickets/WAGER-000-build-wagercore.md)
  - [WAGER-010 — Project foundation](tickets/WAGER-010-project-foundation.md)
  - [WAGER-020 — Wallet and immutable ledger](tickets/WAGER-020-wallet-ledger.md)
  - [WAGER-030 — Crash game engine](tickets/WAGER-030-crash-game-engine.md)
  - [WAGER-040 — Betting and cash-out workflows](tickets/WAGER-040-betting-cashout.md)
  - [WAGER-050 — Reliable messaging and settlement](tickets/WAGER-050-messaging-settlement.md)
  - [WAGER-060 — Identity, tenancy, and responsible gaming](tickets/WAGER-060-identity-tenancy.md)
  - [WAGER-070 — Test harness and observability](tickets/WAGER-070-quality-observability.md)
  - [WAGER-080 — Payments, delivery, and portfolio documentation](tickets/WAGER-080-payments-delivery.md)

Suggested status values: `Backlog`, `Ready`, `In Progress`, `Blocked`, `Done`.

## Delivery order

Start with WAGER-010, then WAGER-020. WAGER-030 and WAGER-040 can follow once the wallet contract exists. WAGER-050 should be introduced after the synchronous domain behavior is proven. WAGER-060 and WAGER-070 are cross-cutting and should evolve with every feature. Finish with WAGER-080.
