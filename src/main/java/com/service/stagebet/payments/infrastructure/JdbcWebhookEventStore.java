package com.service.stagebet.payments.infrastructure;

import com.service.stagebet.payments.application.WebhookEventStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcWebhookEventStore implements WebhookEventStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWebhookEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String eventId, String eventType, String paymentReference, Instant receivedAt) {
        return jdbc.update("""
                INSERT INTO payment_webhook_events (event_id, event_type, payment_reference, received_at)
                VALUES (:eventId, :eventType, :paymentReference, :receivedAt)
                ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("eventId", eventId)
                .addValue("eventType", eventType).addValue("paymentReference", paymentReference)
                .addValue("receivedAt", Timestamp.from(receivedAt))) == 1;
    }
}
