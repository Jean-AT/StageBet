package com.service.stagebet.messaging.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "stagebet.messaging.claim-lease-seconds=0",
        "stagebet.messaging.publisher-delay-ms=600000"
})
class OutboxPublisherTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearOutbox() {
        jdbc.getJdbcTemplate().update("DELETE FROM outbox_events");
    }

    @Test
    void reclaimsAStaleInFlightEventWithoutLettingTheOldCallbackOverwriteTheNewClaim() {
        UUID eventId = UUID.randomUUID();
        insertPendingEvent(eventId);
        KafkaTemplate<String, String> kafka = org.mockito.Mockito.mock(KafkaTemplate.class);
        OutboxPublisher outboxPublisher = new OutboxPublisher(jdbc, kafka, "test.events", "test.events.dlq", 0);
        CompletableFuture<SendResult<String, String>> firstAttempt = new CompletableFuture<>();
        CompletableFuture<SendResult<String, String>> restartedAttempt = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(firstAttempt, restartedAttempt);

        inTransaction(outboxPublisher::publishReadyEvents);
        inTransaction(outboxPublisher::publishReadyEvents);
        firstAttempt.completeExceptionally(new IllegalStateException("publisher crashed after sending"));

        assertEquals("IN_FLIGHT", jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = :id",
                new MapSqlParameterSource("id", eventId), String.class));
        assertEquals(2, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id = :id",
                new MapSqlParameterSource("id", eventId), Integer.class));
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    private void insertPendingEvent(UUID eventId) {
        UUID aggregateId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_version,
                    event_sequence, payload, correlation_id)
                VALUES (:id, 'BET', :aggregateId, 'bet.accepted.v1', 1, 1,
                    CAST('{"status":"ACCEPTED"}' AS JSONB), :correlationId)
                """, new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("aggregateId", aggregateId)
                .addValue("correlationId", UUID.randomUUID()));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
