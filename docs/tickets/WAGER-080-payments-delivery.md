# WAGER-080 — Payments, delivery, and portfolio documentation

**Parent:** WAGER-000  
**Status:** Done
**Dependencies:** WAGER-050, WAGER-060, WAGER-070

## Subtasks

- [x] Define `PaymentGateway` port and local fake payment adapter.
- [x] Add Stripe Sandbox adapter for PaymentIntents, refunds, signed webhooks, duplicates, and failures.
- [x] Document that Stripe is sandbox-only and unsuitable as a production gambling provider.
- [x] Add Docker images and production-like configuration without real credentials.
- [x] Add GitHub Actions for build, tests, static checks, image build, and security scanning.
- [x] Document backup, restore, migration, replay, and incident procedures.
- [x] Write an English README with architecture diagram, setup, API examples, and demo script.
- [x] Record the five proof scenarios: concurrency, idempotency, outbox recovery, DLQ replay, and distributed tracing.

## Acceptance criteria

- No real-money payment or withdrawal path exists.
- Webhook processing is signature-validated, idempotent, and tested with duplicate delivery.
- CI blocks merges on compilation or required test failures.
- A reviewer can run the project locally and understand the design decisions within fifteen minutes.
