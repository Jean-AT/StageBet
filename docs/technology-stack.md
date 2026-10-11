# WagerCore technology stack

This is the technology decision record for the project. It distinguishes dependencies already present in the repository from technologies intentionally deferred until a feature requires them.

## Selected baseline

| Area | Technology | Status | Decision |
|---|---|---|---|
| Language | Java 21 | Present | Use records, sealed types where useful, and modern Java APIs while keeping domain code readable. |
| Application framework | Spring Boot 4.1.1 | Present | Main runtime and configuration framework. Reconfirm compatibility before production deployment. |
| Build | Maven Wrapper | Present | Reproducible local and CI builds through `./mvnw`. |
| Web API | Spring Web MVC | Present | REST commands and queries. |
| Real-time API | Spring WebSocket | Present | Publish authoritative Crash-round updates to clients. |
| Primary database | PostgreSQL | Present | Source of truth for domain state, wallet, ledger, bets, and audit data. |
| Persistence | Spring Data JPA | Selected | JPA is the application persistence approach and Spring Modulith's JPA event publication repository. Spring Data JDBC is intentionally not included to avoid duplicate event-publication repositories. |
| Schema migration | Flyway | Present | Version all schema changes; never rely on auto-generated production schema. |
| Cache/ephemeral state | Redis | Present | Cache, rate limiting, sessions, and short-lived coordination only; never the source of truth for money. |
| Messaging | Kafka, Kafka Streams, Spring Kafka | Present | Durable domain events, outbox publication, retries, and dead-letter handling. |
| Modular architecture | Spring Modulith | Present | Start as a modular monolith with explicit module boundaries before considering service extraction. |
| Security | Spring Security and OAuth2 modules | Present | Resource-server JWT validation first; authorization server/client configuration only when required by the deployment model. |
| Local orchestration | Spring Boot Docker Compose support | Present | Start local infrastructure consistently. Pin image versions before relying on CI or demos. |
| Code reduction | Lombok | Present | Use selectively; do not hide domain behavior behind generated code. |
| Testing | Spring Boot and Spring Modulith test modules | Present | Unit, application, integration, and module-boundary tests. |

## Planned additions

These are part of the WagerCore target architecture but should be introduced with the ticket that needs them:

- Testcontainers for PostgreSQL, Kafka, and Redis integration tests.
- WireMock for payment/provider failure and webhook tests.
- Micrometer and Prometheus-compatible metrics.
- OpenTelemetry for distributed traces and context propagation.
- Grafana, Loki, and Tempo for local observability demonstrations.
- Resilience4j or a Spring-native equivalent for timeout, retry, and circuit-breaker policies after the failure contracts are defined.
- Keycloak or another external identity provider for local OAuth2/JWT flows.
- GraphQL only for administrative read models if REST queries become insufficient.
- k6 for load and concurrency scenarios.
- GitHub Actions, Docker image build, GHCR publishing, and AWS deployment configuration.

## Deliberately excluded for now

- MongoDB: no current domain requirement; raw provider payloads can be added later if justified.
- Stripe production payments: not appropriate for a gambling product. Use a fake provider and Stripe Sandbox adapter only for educational webhook/idempotency demonstrations.
- Multiple brokers: use Kafka as the chosen event platform; do not add RabbitMQ or SQS merely to list technologies.
- Redis as financial truth: balances and ledger entries belong in PostgreSQL.

## Foundation rules

- Use camelCase for Java methods, parameters, fields, local variables, test methods, and serialized properties.
- Use `BigDecimal` or integer minor units for monetary values; never `double`.
- Keep domain rules independent from framework adapters where practical.
- Treat Kafka as at-least-once delivery and achieve one business effect with idempotency and database constraints.
- Make version and image changes explicit in this document and in the relevant ticket.

## Local ports

- PostgreSQL: `localhost:5433` → container `5432` (host `5432` was already occupied when the local stack was started).
- Redis: `localhost:6379`.
- Kafka: `localhost:9092`.
