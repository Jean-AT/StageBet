package com.service.stagebet.messaging.application;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {

    private final NamedParameterJdbcTemplate jdbc;

    public InboxService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean claim(String consumerName, UUID eventId, UUID correlationId) {
        return jdbc.update("INSERT INTO inbox_events (consumer_name, event_id, correlation_id) "
                + "VALUES (:consumerName, :eventId, :correlationId) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource().addValue("consumerName", consumerName)
                        .addValue("eventId", eventId).addValue("correlationId", correlationId)) == 1;
    }
}
