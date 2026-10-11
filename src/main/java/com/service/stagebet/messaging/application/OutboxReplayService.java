package com.service.stagebet.messaging.application;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxReplayService {

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxReplayService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean replay(UUID eventId) {
        return jdbc.update("UPDATE outbox_events SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP, "
                + "last_error = NULL WHERE id = :id AND status = 'DEAD'", new MapSqlParameterSource("id", eventId)) == 1;
    }
}
