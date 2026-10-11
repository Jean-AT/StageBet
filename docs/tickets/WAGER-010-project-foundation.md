# WAGER-010 — Project foundation

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** None

## Current repository baseline

The initial project already declares these technologies in `pom.xml`:

- Java 21
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL JDBC driver
- Spring Data JPA and Spring Data JDBC
- Flyway with PostgreSQL support
- Redis and Redis-backed HTTP sessions
- Apache Kafka, Kafka Streams, and Spring Kafka
- Spring Modulith core, JDBC, JPA, events API, and Kafka integration
- Spring Web MVC and WebSocket
- Spring Security plus OAuth2 authorization-server, client, and resource-server modules
- Spring Boot Docker Compose support and DevTools
- Lombok
- Spring Boot/Spring Modulith test modules

The authoritative rationale and status of each technology is documented in [technology-stack.md](../technology-stack.md).

The following items are planned but are not yet represented in the current `pom.xml`: OpenTelemetry, Micrometer/Prometheus export, WireMock, Testcontainers, k6, Keycloak runtime configuration, GraphQL, Resilience4j, and CI/container publishing configuration. They should be added only when the corresponding ticket needs them.

## Subtasks

- [ ] Confirm Java 21, Spring Boot, Maven, and dependency versions against [technology-stack.md](../technology-stack.md).
- [ ] Define feature-oriented package/module boundaries.
- [x] Choose Spring Data JPA as the application persistence approach and document the boundary.
- [x] Pin local PostgreSQL and Redis image versions and add Kafka to `compose.yaml`.
- [x] Configure an empty local `stagebet` PostgreSQL database through environment-backed `application.yaml` defaults.
- [ ] Add Flyway baseline and environment-specific configuration.
- [ ] Add API error format, validation, correlation ID, and structured logging foundations.
- [ ] Add ADRs for modular monolith, PostgreSQL ownership, Kafka usage, and money representation.
- [ ] Add health/readiness endpoints and a local developer startup guide.

## Acceptance criteria

- A new developer can start dependencies and run the application from a clean checkout.
- The application starts with local configuration and fails clearly when required configuration is missing.
- Package boundaries are documented and enforced where practical.
- A sample endpoint demonstrates validation and the standard error contract.
- The selected technology versions and the reason for each major dependency are documented.

## Initial verification

`./mvnw test` currently compiles the project but the context-load test fails because the application has no datasource URL/credentials configured and no PostgreSQL service is running for the test. This is an expected foundation gap and is covered by the Flyway/configuration/Compose subtasks above; it must be resolved before marking this ticket `Done`.
