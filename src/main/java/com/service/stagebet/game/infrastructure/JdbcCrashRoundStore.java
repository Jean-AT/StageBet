package com.service.stagebet.game.infrastructure;

import com.service.stagebet.game.application.CrashRoundStore;
import com.service.stagebet.game.domain.CrashRound;
import com.service.stagebet.game.domain.CrashRoundSnapshot;
import com.service.stagebet.game.domain.RoundStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCrashRoundStore implements CrashRoundStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCrashRoundStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(CrashRound round) {
        CrashRoundSnapshot snapshot = round.snapshot();
        String sql = """
                INSERT INTO crash_rounds (
                    id, status, server_seed, server_seed_hash, crash_multiplier, last_multiplier,
                    event_sequence, scheduled_at, opened_at, started_at, crashed_at, settled_at, version
                ) VALUES (
                    :id, :status, :serverSeed, :serverSeedHash, :crashMultiplier, :lastMultiplier,
                    :eventSequence, :scheduledAt, :openedAt, :startedAt, :crashedAt, :settledAt, :version
                )
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    server_seed = EXCLUDED.server_seed,
                    server_seed_hash = EXCLUDED.server_seed_hash,
                    crash_multiplier = EXCLUDED.crash_multiplier,
                    last_multiplier = EXCLUDED.last_multiplier,
                    event_sequence = EXCLUDED.event_sequence,
                    scheduled_at = EXCLUDED.scheduled_at,
                    opened_at = EXCLUDED.opened_at,
                    started_at = EXCLUDED.started_at,
                    crashed_at = EXCLUDED.crashed_at,
                    settled_at = EXCLUDED.settled_at,
                    version = EXCLUDED.version,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", snapshot.id())
                .addValue("status", snapshot.status().name())
                .addValue("serverSeed", snapshot.serverSeed())
                .addValue("serverSeedHash", snapshot.serverSeedHash())
                .addValue("crashMultiplier", snapshot.crashMultiplier())
                .addValue("lastMultiplier", snapshot.lastMultiplier())
                .addValue("eventSequence", snapshot.eventSequence())
                .addValue("scheduledAt", Timestamp.from(snapshot.scheduledAt()))
                .addValue("openedAt", timestamp(snapshot.openedAt()))
                .addValue("startedAt", timestamp(snapshot.startedAt()))
                .addValue("crashedAt", timestamp(snapshot.crashedAt()))
                .addValue("settledAt", timestamp(snapshot.settledAt()))
                .addValue("version", snapshot.version()));
    }

    @Override
    public Optional<CrashRound> findById(UUID roundId) {
        return find(roundId, false);
    }

    @Override
    public Optional<CrashRound> findByIdForUpdate(UUID roundId) {
        return find(roundId, true);
    }

    private Optional<CrashRound> find(UUID roundId, boolean forUpdate) {
        String sql = "SELECT id, status, server_seed, server_seed_hash, crash_multiplier, last_multiplier, "
                + "event_sequence, scheduled_at, opened_at, started_at, crashed_at, settled_at, version "
                + "FROM crash_rounds WHERE id = :id" + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, Map.of("id", roundId), rows -> {
            if (!rows.next()) {
                return Optional.empty();
            }
            return Optional.of(CrashRound.restore(snapshot(rows)));
        });
    }

    private CrashRoundSnapshot snapshot(ResultSet resultSet) throws SQLException {
        return new CrashRoundSnapshot(
                resultSet.getObject("id", UUID.class),
                RoundStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("server_seed"),
                resultSet.getString("server_seed_hash"),
                resultSet.getBigDecimal("crash_multiplier"),
                resultSet.getBigDecimal("last_multiplier"),
                resultSet.getLong("event_sequence"),
                resultSet.getTimestamp("scheduled_at").toInstant(),
                instant(resultSet, "opened_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "crashed_at"),
                instant(resultSet, "settled_at"),
                resultSet.getLong("version"));
    }

    private Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
