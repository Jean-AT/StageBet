# StageBet

StageBet is a Java 21/Spring Boot modular monolith for a virtual-credit Crash game.
It demonstrates wallet correctness, idempotent betting, responsible gaming, reliable
outbox delivery, signed payment webhooks, and production-oriented observability.

It does not process real money. Stripe support is sandbox-only and disabled by default.

## Architecture

```text
HTTP + WebSocket
       |
       v
  feature modules ── PostgreSQL (wallet, bets, audit, outbox)
       |                         |
       +── Redis (rate limits)   +── Kafka (at-least-once events)
       |
       +── optional Stripe Sandbox adapter (signed webhooks, no withdrawals)
```

PostgreSQL is the source of truth for money and game state. Kafka is at-least-once;
database uniqueness and idempotency keys provide one business effect. Redis is only used
for ephemeral coordination and rate limiting.

## Run locally

Prerequisites: Java 21, Maven, and Docker Compose.

```bash
docker compose up -d
mvn spring-boot:run
```

The API is available at `http://localhost:8080`. Health and metrics are exposed at
`/actuator/health` and `/actuator/prometheus`.

## API examples

Place a virtual-credit bet:

```bash
curl -X POST http://localhost:8080/api/tenants/$TENANT_ID/players/$PLAYER_ID/rounds/$ROUND_ID/bets \
  -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: demo-bet-1' \
  -d '{"betId":"'$BET_ID'","amount":"10.00","currency":"USD","expectedRoundSequence":1}'
```

Stripe Sandbox webhooks must include a valid `Stripe-Signature` header. Duplicate event
IDs return `DUPLICATE` without applying the event twice.

## Verify

```bash
mvn test
mvn test -Dtest='*IntegrationTest' -DexcludedTestGroups=
```

The second command requires Docker. CI runs compilation, tests, a container build, and a
high/critical vulnerability scan.

## Demo proof scenarios

1. Wallet reservation concurrency: `WalletOperationServiceTest`.
2. HTTP idempotency: `BettingControllerTest` and `BettingServiceTest`.
3. Outbox recovery: `OutboxPublisher` failure path and WAGER-070 failure-injection runbook.
4. DLQ/replay behavior: messaging module and `docs/operations/WAGER-070-failure-injection.md`.
5. Distributed tracing: correlation IDs, Micrometer tracing, and OTLP configuration.

Backup, restore, migration, replay, and incident procedures are documented in
`docs/operations/WAGER-080-runbook.md`.
