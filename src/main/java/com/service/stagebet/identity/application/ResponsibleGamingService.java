package com.service.stagebet.identity.application;

import com.service.stagebet.identity.domain.ResponsibleGamingException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResponsibleGamingService implements ResponsibleGamingPolicy {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public ResponsibleGamingService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this(jdbc, auditService, Clock.systemUTC());
    }

    ResponsibleGamingService(NamedParameterJdbcTemplate jdbc, AuditService auditService, Clock clock) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void validateAndRecordBet(UUID tenantId, UUID playerId, BigDecimal amount) {
        ensureProfile(tenantId, playerId);
        Profile profile = jdbc.queryForObject("SELECT daily_bet_limit, session_bet_limit, daily_bet_total, "
                + "session_bet_total, daily_total_date, session_started_at, self_excluded_until, cool_off_until, suspended "
                + "FROM responsible_gaming_profiles WHERE tenant_id = :tenantId AND player_id = :playerId FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId),
                (rs, rowNum) -> new Profile(rs.getBigDecimal("daily_bet_limit"), rs.getBigDecimal("session_bet_limit"),
                        rs.getBigDecimal("daily_bet_total"), rs.getBigDecimal("session_bet_total"),
                        rs.getDate("daily_total_date").toLocalDate(), rs.getTimestamp("session_started_at").toInstant(),
                        timestamp(rs, "self_excluded_until"), timestamp(rs, "cool_off_until"), rs.getBoolean("suspended")));
        Instant now = clock.instant();
        if (profile.suspended()) {
            throw new ResponsibleGamingException("player account is suspended");
        }
        if (profile.selfExcludedUntil() != null && now.isBefore(profile.selfExcludedUntil())) {
            throw new ResponsibleGamingException("player is self-excluded");
        }
        if (profile.coolOffUntil() != null && now.isBefore(profile.coolOffUntil())) {
            throw new ResponsibleGamingException("player is in a cool-off period");
        }
        BigDecimal dailyTotal = profile.dailyTotalDate().equals(java.time.LocalDate.now(clock))
                ? profile.dailyBetTotal() : BigDecimal.ZERO.setScale(2);
        BigDecimal sessionTotal = profile.sessionStartedAt().plusSeconds(24 * 60 * 60).isBefore(now)
                ? BigDecimal.ZERO.setScale(2) : profile.sessionBetTotal();
        if (profile.dailyBetLimit() != null && dailyTotal.add(amount).compareTo(profile.dailyBetLimit()) > 0) {
            throw new ResponsibleGamingException("daily betting limit exceeded");
        }
        if (profile.sessionBetLimit() != null && sessionTotal.add(amount).compareTo(profile.sessionBetLimit()) > 0) {
            throw new ResponsibleGamingException("session betting limit exceeded");
        }
        jdbc.update("UPDATE responsible_gaming_profiles SET daily_bet_total = :dailyTotal, session_bet_total = :sessionTotal, "
                + "daily_total_date = CURRENT_DATE, session_started_at = CASE WHEN :resetSession THEN CURRENT_TIMESTAMP ELSE session_started_at END, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = :tenantId AND player_id = :playerId",
                new MapSqlParameterSource().addValue("dailyTotal", dailyTotal.add(amount))
                        .addValue("sessionTotal", sessionTotal.add(amount))
                        .addValue("resetSession", profile.sessionStartedAt().plusSeconds(24 * 60 * 60).isBefore(now))
                        .addValue("tenantId", tenantId).addValue("playerId", playerId));
    }

    @Transactional
    public void updateRestrictions(UUID tenantId, UUID playerId, UUID actorId, UUID correlationId,
            BigDecimal dailyLimit, BigDecimal sessionLimit, Instant selfExcludedUntil,
            Instant coolOffUntil, boolean suspended) {
        ensureProfile(tenantId, playerId);
        jdbc.update("UPDATE responsible_gaming_profiles SET daily_bet_limit = :dailyLimit, session_bet_limit = :sessionLimit, "
                + "self_excluded_until = :selfExcludedUntil, cool_off_until = :coolOffUntil, suspended = :suspended, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = :tenantId AND player_id = :playerId",
                new MapSqlParameterSource().addValue("dailyLimit", dailyLimit).addValue("sessionLimit", sessionLimit)
                        .addValue("selfExcludedUntil", timestamp(selfExcludedUntil)).addValue("coolOffUntil", timestamp(coolOffUntil))
                        .addValue("suspended", suspended).addValue("tenantId", tenantId).addValue("playerId", playerId));
        auditService.record(tenantId, actorId, "RESPONSIBLE_GAMING_RESTRICTIONS_UPDATED", "PLAYER", playerId,
                correlationId, "{\"suspended\":" + suspended + "}");
    }

    private void ensureProfile(UUID tenantId, UUID playerId) {
        jdbc.update("INSERT INTO responsible_gaming_profiles (tenant_id, player_id) VALUES (:tenantId, :playerId) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId));
    }

    private Instant timestamp(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private java.sql.Timestamp timestamp(Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
    }

    private record Profile(BigDecimal dailyBetLimit, BigDecimal sessionBetLimit, BigDecimal dailyBetTotal,
            BigDecimal sessionBetTotal, java.time.LocalDate dailyTotalDate, Instant sessionStartedAt,
            Instant selfExcludedUntil, Instant coolOffUntil, boolean suspended) {
    }
}
