# WAGER-020 — Wallet and immutable ledger

**Parent:** WAGER-000  
**Status:** Done
**Dependencies:** WAGER-010

## Subtasks

- [x] Create the first Flyway wallet/ledger schema for accounts, holds, transactions, entries, and idempotency records.
- [x] Add the immutable `Money` value object with currency and scale validation.
- [x] Implement transactional deposit and reserve operations with PostgreSQL row locking.
- [x] Add idempotent deposit/reserve handling and insufficient-funds protection.
- [x] Add concurrent reservation coverage proving twenty requests accept exactly ten from a 100-credit wallet.
- [x] Add reconciliation comparing available balances to ledger totals and reserved balances to active holds.
- [x] Add duplicate-operation coverage for release, capture, and payout.
- [x] Model player wallets, accounts, holds, ledger entries, and transaction references in the database and application command layer.
- [ ] Implement virtual-credit deposit, reserve, capture, release, and payout use cases.
- [ ] Use `BigDecimal` with explicit scale and currency rules.
- [ ] Make ledger entries append-only and auditable.
- [ ] Add atomic balance protection with database constraints and an explicit locking strategy.
- [ ] Add idempotency keys for every money-affecting command.
- [ ] Add reconciliation query comparing wallet balances to ledger totals.

## Acceptance criteria

- Balance never becomes negative under concurrent reservations.
- Repeating a command with the same idempotency key returns the original result.
- Every balance change has an immutable reference, reason, actor, and timestamp.
- Tests prove that twenty concurrent ten-credit bets accept exactly ten bets from a one-hundred-credit balance.
