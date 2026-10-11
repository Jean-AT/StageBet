# WAGER-070 — Test harness and observability

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** WAGER-010

## Subtasks

- [ ] Add unit tests for domain rules and state transitions.
- [ ] Add Testcontainers integration tests for PostgreSQL, Kafka, and Redis.
- [ ] Add WireMock tests for provider failures and duplicate webhooks.
- [ ] Add deterministic test clock, ID generator, seed generator, and fixture builders.
- [ ] Add concurrency tests for reservations, cash-outs, and settlement.
- [ ] Add k6 scenarios for betting, WebSocket observation, and API limits.
- [ ] Add OpenTelemetry traces, Prometheus metrics, Grafana dashboards, and structured logs.
- [ ] Add failure-injection scenarios for database, Kafka, Redis, and provider timeouts.

## Acceptance criteria

- Tests are repeatable without arbitrary sleeps.
- CI can run the default test suite from a clean checkout.
- A trace follows a bet from HTTP request through wallet and settlement.
- Dashboards expose error rate, latency, consumer lag, DLQ count, settlement failures, and reconciliation drift.
