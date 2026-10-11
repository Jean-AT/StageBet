package com.service.stagebet.messaging.infrastructure;

import com.service.stagebet.messaging.application.OutboxEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final int MAX_ATTEMPTS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final String deadLetterTopic;

    public OutboxPublisher(NamedParameterJdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
            @Value("${stagebet.messaging.topic:wager.events.v1}") String topic,
            @Value("${stagebet.messaging.dead-letter-topic:wager.events.dlq.v1}") String deadLetterTopic) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;
        this.deadLetterTopic = deadLetterTopic;
    }

    @Scheduled(fixedDelayString = "${stagebet.messaging.publisher-delay-ms:1000}")
    @Transactional
    public void publishReadyEvents() {
        List<OutboxEvent> events = claimReadyEvents(50);
        events.forEach(this::publish);
    }

    private List<OutboxEvent> claimReadyEvents(int batchSize) {
        return jdbc.query("""
                SELECT id, aggregate_type, aggregate_id, event_type, event_version, event_sequence,
                    payload, correlation_id, causation_id
                FROM outbox_events
                WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """, new MapSqlParameterSource("batchSize", batchSize), (rs, rowNum) -> {
                    OutboxEvent event = event(rs);
                    jdbc.update("UPDATE outbox_events SET status = 'IN_FLIGHT', attempts = attempts + 1 WHERE id = :id",
                            new MapSqlParameterSource("id", event.id()));
                    return event;
                });
    }

    private void publish(OutboxEvent event) {
        kafka.send(topic, event.aggregateId().toString(), envelope(event)).whenComplete((ignored, failure) -> {
            if (failure == null) {
                markPublished(event.id());
            } else {
                markFailed(event, failure);
            }
        });
    }

    private void markPublished(UUID eventId) {
        jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE id = :id",
                new MapSqlParameterSource("id", eventId));
    }

    private void markFailed(OutboxEvent event, Throwable failure) {
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id = :id",
                new MapSqlParameterSource("id", event.id()), Integer.class);
        boolean dead = attempts != null && attempts >= MAX_ATTEMPTS;
        jdbc.update("UPDATE outbox_events SET status = :status, next_attempt_at = CURRENT_TIMESTAMP + (:delay * INTERVAL '1 second'), "
                        + "last_error = :error WHERE id = :id",
                new MapSqlParameterSource().addValue("status", dead ? "DEAD" : "PENDING")
                        .addValue("delay", backoffSeconds(attempts == null ? 1 : attempts))
                        .addValue("error", failure.toString()).addValue("id", event.id()));
        if (dead) {
            kafka.send(deadLetterTopic, event.aggregateId().toString(), envelope(event));
        }
    }

    private String envelope(OutboxEvent event) {
        return "{\"eventId\":\"" + event.id() + "\",\"eventType\":\"" + event.eventType()
                + "\",\"eventVersion\":" + event.eventVersion() + ",\"aggregateType\":\""
                + event.aggregateType() + "\",\"aggregateId\":\"" + event.aggregateId()
                + "\",\"correlationId\":\"" + event.correlationId() + "\",\"causationId\":"
                + (event.causationId() == null ? "null" : "\"" + event.causationId() + "\"")
                + ",\"payload\":" + event.payload() + "}";
    }

    private long backoffSeconds(int attempts) {
        return Math.min(60, 1L << Math.min(attempts, 6));
    }

    private OutboxEvent event(ResultSet rs) throws SQLException {
        return new OutboxEvent(rs.getObject("id", UUID.class), rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"), rs.getInt("event_version"),
                nullableLong(rs, "event_sequence"), rs.getString("payload"), rs.getObject("correlation_id", UUID.class),
                rs.getObject("causation_id", UUID.class));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
