# WAGER-070 — Test harness and observability

**Parent:** WAGER-000  
**Status:** Done
**Dependencies:** WAGER-010

## Subtasks

- [x] Add unit tests for domain rules and state transitions.
- [x] Add Testcontainers integration tests for PostgreSQL, Kafka, and Redis.
- [x] Add WireMock tests for provider failures and duplicate webhooks.
- [x] Add deterministic test clock, ID generator, seed generator, and fixture builders.
- [x] Add concurrency tests for reservations, cash-outs, and settlement.
- [x] Add k6 scenarios for betting, WebSocket observation, and API limits.
- [x] Add OpenTelemetry traces, Prometheus metrics, Grafana dashboards, and structured logs.
- [x] Add failure-injection scenarios for database, Kafka, Redis, and provider timeouts.

## Acceptance criteria

- Tests are repeatable without arbitrary sleeps.
- CI can run the default test suite from a clean checkout.
- A trace follows a bet from HTTP request through wallet and settlement.
- Dashboards expose error rate, latency, consumer lag, DLQ count, settlement failures, and reconciliation drift.

## Delivery notes

The default Maven suite remains self-contained and excludes tests tagged `integration`.
CI or local Docker runs can enable the infrastructure profile with
`mvn test -DexcludedTestGroups= -Dgroups=integration`. Failure-injection procedures are
documented in `docs/operations/WAGER-070-failure-injection.md`.
