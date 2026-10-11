# WAGER-000 — Build WagerCore iGaming Platform

**Type:** Parent task  
**Status:** Backlog  
**Priority:** High  
**Target:** Portfolio-ready Java backend project

## Goal

Build a multi-tenant iGaming backend centered on a virtual-credit Crash game. The project should demonstrate reliable financial workflows, concurrency control, event-driven integration, testing, observability, and responsible gaming without processing real money.

## Scope

- Java 21 and Spring Boot
- PostgreSQL as the source of truth
- Wallet with immutable double-entry-style ledger
- Crash game with authoritative server-side settlement
- REST commands and WebSocket round updates
- Kafka/outbox messaging where asynchronous behavior adds value
- Redis only for cache, rate limiting, and ephemeral coordination
- OAuth2/JWT roles and tenant isolation
- Stripe Sandbox adapter plus a local fake payment provider
- Testcontainers, WireMock, concurrency tests, k6, and observability

## Explicit non-goals

- Real-money gambling or withdrawals
- Using Stripe as a production gambling provider
- Copying Aviator branding, assets, or proprietary algorithms
- Claiming exactly-once message delivery
- Adding microservices before the modular monolith proves the domain boundaries

## Definition of done

- A player can fund virtual balance, place a bet, cash out, and receive deterministic settlement.
- Duplicate commands and Kafka deliveries have one business effect.
- Concurrent requests cannot create a negative balance or double payout.
- A failed message is retried, dead-lettered, and replayable.
- Critical flows are traceable and covered by automated tests.
- README, architecture decisions, threat model, runbook, and demo script are complete.
