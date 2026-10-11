package com.service.stagebet.identity.application;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final NamedParameterJdbcTemplate jdbc;

    public AuditService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID tenantId, UUID actorId, String action, String subjectType, UUID subjectId,
            UUID correlationId, String details) {
        jdbc.update("INSERT INTO audit_records (id, tenant_id, actor_id, action, subject_type, subject_id, correlation_id, details) "
                + "VALUES (:id, :tenantId, :actorId, :action, :subjectType, :subjectId, :correlationId, CAST(:details AS JSONB))",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("actorId", actorId).addValue("action", action).addValue("subjectType", subjectType)
                        .addValue("subjectId", subjectId).addValue("correlationId", correlationId).addValue("details", details));
    }
}
