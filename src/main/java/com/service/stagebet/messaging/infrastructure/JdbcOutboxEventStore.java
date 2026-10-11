package com.service.stagebet.messaging.infrastructure;

import com.service.stagebet.messaging.application.OutboxEventStore;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxEventStore implements OutboxEventStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOutboxEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
            Long eventSequence, String payload, UUID correlationId, UUID causationId) {
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_version,
                    event_sequence, payload, correlation_id, causation_id)
                VALUES (:id, :aggregateType, :aggregateId, :eventType, :eventVersion,
                    :eventSequence, CAST(:payload AS JSONB), :correlationId, :causationId)
                ON CONFLICT (aggregate_type, aggregate_id, event_type, event_sequence) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId)
                .addValue("eventType", eventType)
                .addValue("eventVersion", eventVersion)
                .addValue("eventSequence", eventSequence)
                .addValue("payload", payload)
                .addValue("correlationId", correlationId)
                .addValue("causationId", causationId));
    }
}
