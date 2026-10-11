# WAGER-080 operational runbook

## Backup and restore

Use PostgreSQL-native backups; never copy the data volume while the database is writing.

```bash
pg_dump --format=custom --file=stagebet.dump "$JDBC_DATABASE_URL"
pg_restore --clean --if-exists --dbname="$JDBC_DATABASE_URL" stagebet.dump
```

Restore into an isolated database first, validate Flyway and reconciliation queries, then
switch traffic. Keep credentials in the deployment secret store, never in this repository.

## Migrations

Flyway applies ordered migrations at startup. Review every migration for backward
compatibility, deploy additive changes first, and roll back application code before
considering a compensating migration. Never edit an applied migration.

## Replay and incidents

For Kafka incidents, leave committed outbox rows pending and restart the publisher after
the broker is healthy. For dead-letter events, inspect the payload and correlation ID,
fix the consumer defect, then replay through the inbox/idempotency boundary. Reconciliation
must be green before declaring recovery complete.

Payment webhooks are signature-validated and deduplicated by event ID. Stripe Sandbox is
educational only; no production gambling deposits, withdrawals, or real-money settlement
are supported.
