# WAGER-070 failure injection

The integration profile exercises PostgreSQL, Kafka, Redis, and a WireMock provider.
Run it only with Docker available:

```bash
mvn test -DexcludedTestGroups= -Dgroups=integration
```

For local recovery drills, start dependencies with `docker compose up -d`, then stop one
dependency while sending the k6 scenarios:

```bash
docker compose stop postgres
docker compose stop kafka
docker compose stop redis
```

Expected behavior is a visible error or retryable outbox state; no money operation may
be acknowledged twice. Verify `/actuator/health`, `/actuator/prometheus`, outbox/DLQ
metrics, and correlation IDs after each drill. Restore dependencies with
`docker compose start <service>` before rerunning the default suite.
