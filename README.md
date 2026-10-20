# StageBet

StageBet is a Java 21/Spring Boot modular monolith for a virtual-credit Crash game.
It demonstrates wallet correctness, idempotent betting, responsible gaming, reliable
outbox delivery, signed payment webhooks, and production-oriented observability.

It does not process real money. Stripe support is sandbox-only and disabled by default

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

### Postman

Import `ops/postman/StageBet.postman_collection.json` into the Postman desktop app. It
contains a health check, bet placement, and cash-out with descriptions and example
requests. Set the collection's `tenantId`, `playerId`, and `roundId` variables before
sending a bet; `betId` is generated on first use. Keep the same `betId` and idempotency
key to retry, or clear `betId` for a new bet.

The health check works without login. Betting requires an authenticated request and
an already funded player in an OPEN round; cash-out requires a RUNNING round. This app
does not yet expose public wallet-funding, round-control, or local token-issuing
endpoints. To use a bearer token, configure a JWT issuer and set `accessToken` locally
in Postman. Without these prerequisites, the betting requests will not succeed merely
by importing the collection. Never save a real token in the repository.

## Verify

```bash
mvn test
mvn test -Dtest='*IntegrationTest' -DexcludedTestGroups=
```

The second command requires Docker. CI runs compilation, tests, a container build, and a
high/critical vulnerability scan.

To exercise 100 HTTP bet requests with 20 workers against a funded virtual wallet, then
cash out every accepted bet and reconcile the ledger, start the local Compose services
and run:

```bash
mvn test -Dtest=HundredBetRequestsIntegrationTest -DexcludedTestGroups=
```

The test uses the full Spring request pipeline, PostgreSQL, and Redis. Round setup and
virtual funding use application services because those operations do not have public
HTTP endpoints yet. It uses a test principal for the HTTP betting commands.

For a full game with 100 distinct players, real Redis rate-limit counters, and Kafka
outbox events, run:

```bash
mvn test -Dtest=HundredPlayersGameIntegrationTest -DexcludedTestGroups=
```

This scenario funds each virtual wallet, places 100 HTTP bets with 20 workers,
cashes out 50 players, crashes and settles the other 50, reconciles every wallet,
checks a Redis rate-limit rejection, and consumes the resulting Kafka events. It
requires the local Compose services.
Wallet funding and round transitions use application services because those operations
have no public HTTP endpoints; player betting and cash-out use the HTTP request pipeline
with distinct test principals. This is an in-process integration scenario, not a
deployed-network load test.

## Demo proof scenarios

1. Wallet reservation concurrency: `WalletOperationServiceTest`.
2. HTTP idempotency: `BettingControllerTest` and `BettingServiceTest`.
3. Outbox recovery: `OutboxPublisher` failure path and WAGER-070 failure-injection runbook.
4. DLQ/replay behavior: messaging module and `docs/operations/WAGER-070-failure-injection.md`.
5. Distributed tracing: correlation IDs, Micrometer tracing, and OTLP configuration.

Backup, restore, migration, replay, and incident procedures are documented in
`docs/operations/WAGER-080-runbook.md`.
