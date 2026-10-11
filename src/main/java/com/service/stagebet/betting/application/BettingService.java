package com.service.stagebet.betting.application;

import com.service.stagebet.betting.domain.BetStatus;
import com.service.stagebet.game.domain.RoundStatus;
import com.service.stagebet.messaging.application.EventPayload;
import com.service.stagebet.messaging.application.OutboxEventStore;
import com.service.stagebet.wallet.application.WalletCommand;
import com.service.stagebet.wallet.application.WalletOperationService;
import com.service.stagebet.wallet.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BettingService {

    private final NamedParameterJdbcTemplate jdbc;
    private final WalletOperationService wallet;
    private final OutboxEventStore outbox;
    private final Clock clock;

    @Autowired
    public BettingService(NamedParameterJdbcTemplate jdbc, WalletOperationService wallet,
            OutboxEventStore outbox) {
        this(jdbc, wallet, outbox, Clock.systemUTC());
    }

    BettingService(NamedParameterJdbcTemplate jdbc, WalletOperationService wallet,
            OutboxEventStore outbox, Clock clock) {
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public BetResult placeBet(PlaceBetCommand command) {
        BetRow duplicate = findByPlacementKey(command.tenantId(), command.idempotencyKey());
        if (duplicate != null) {
            return result(duplicate, true);
        }
        new Money(command.amount(), command.currency());

        RoundRow round = lockRound(command.roundId());
        if (round.status() != RoundStatus.OPEN || round.eventSequence() != command.expectedRoundSequence()) {
            throw new IllegalStateException("bets are accepted only for the current OPEN round sequence");
        }

        UUID holdId = command.betId();
        wallet.reserve(new WalletCommand(command.tenantId(), command.playerId(), holdId, command.amount(),
                command.currency(), "bet-reserve:" + command.idempotencyKey(), command.actorId()));
        jdbc.update("""
                INSERT INTO bets (id, tenant_id, player_id, round_id, amount, currency, status, hold_id,
                    accepted_sequence, idempotency_key)
                VALUES (:id, :tenantId, :playerId, :roundId, :amount, :currency, 'ACCEPTED', :holdId,
                    :sequence, :idempotencyKey)
                """, params(command).addValue("holdId", holdId).addValue("sequence", round.eventSequence()));
        BetRow bet = new BetRow(command.betId(), command.roundId(), command.playerId(), command.amount(), command.currency().toUpperCase(),
                BetStatus.ACCEPTED, holdId, round.eventSequence(), null, null, null);
        publish(bet, round.eventSequence(), null);
        return result(bet, false);
    }

    @Transactional
    public BetResult cashOut(CashOutCommand command) {
        CashOutRow duplicate = findCashOut(command.tenantId(), command.idempotencyKey());
        if (duplicate != null) {
            return duplicate.result(true);
        }

        BetRow betReference = findBetById(command.tenantId(), command.playerId(), command.betId());
        if (betReference == null) {
            throw new IllegalArgumentException("bet does not exist: " + command.betId());
        }
        RoundRow round = lockRound(betReference.roundId());
        BetRow bet = lockBet(command.tenantId(), command.playerId(), command.betId());
        if (bet.status() != BetStatus.ACCEPTED) {
            throw new IllegalStateException("bet is no longer cashable: " + bet.status());
        }
        if (round.status() != RoundStatus.RUNNING || round.eventSequence() != command.expectedRoundSequence()) {
            throw new IllegalStateException("cash-out lost the race with the round or has a stale sequence");
        }
        BigDecimal payout = bet.amount().multiply(round.lastMultiplier()).setScale(2, RoundingMode.UNNECESSARY);
        wallet.capture(command.tenantId(), bet.holdId(), command.actorId(), "bet-capture:" + command.idempotencyKey());
        wallet.payout(new WalletCommand(command.tenantId(), command.playerId(), bet.id(), payout, bet.currency(),
                "bet-payout:" + command.idempotencyKey(), command.actorId()));
        jdbc.update("UPDATE bets SET status = 'CASHED_OUT', cash_out_sequence = :sequence, "
                + "cash_out_multiplier = :multiplier, payout_amount = :payout, cashed_out_at = CURRENT_TIMESTAMP WHERE id = :id",
                new MapSqlParameterSource().addValue("id", bet.id()).addValue("sequence", round.eventSequence())
                        .addValue("multiplier", round.lastMultiplier()).addValue("payout", payout));
        jdbc.update("INSERT INTO bet_cash_outs (id, tenant_id, bet_id, idempotency_key, round_sequence, multiplier, payout_amount) "
                + "VALUES (:id, :tenantId, :betId, :key, :sequence, :multiplier, :payout)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("tenantId", command.tenantId())
                        .addValue("betId", bet.id()).addValue("key", command.idempotencyKey())
                        .addValue("sequence", round.eventSequence()).addValue("multiplier", round.lastMultiplier())
                        .addValue("payout", payout));
        BetRow result = bet.withCashOut(round.eventSequence(), round.lastMultiplier(), payout);
        publish(result, round.eventSequence(), payout);
        return result(result, false);
    }

    @Transactional
    public int settleCrashedRound(UUID tenantId, UUID roundId, UUID actorId) {
        RoundRow round = lockRound(roundId);
        if (round.status() != RoundStatus.CRASHED) {
            throw new IllegalStateException("only a CRASHED round can settle bets");
        }
        var bets = jdbc.query("SELECT id, round_id, player_id, amount, currency, status, hold_id, accepted_sequence, "
                + "cash_out_sequence, cash_out_multiplier, payout_amount FROM bets WHERE tenant_id = :tenantId "
                + "AND round_id = :roundId AND status = 'ACCEPTED' FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("roundId", roundId), this::betRow);
        for (BetRow bet : bets) {
            wallet.capture(tenantId, bet.holdId(), actorId, "bet-loss:" + bet.id());
            jdbc.update("UPDATE bets SET status = 'LOST', settled_at = CURRENT_TIMESTAMP WHERE id = :id",
                    new MapSqlParameterSource("id", bet.id()));
            publish(bet.withStatus(BetStatus.LOST), round.eventSequence(), null);
        }
        return bets.size();
    }

    @Transactional
    public int refundCancelledRound(UUID tenantId, UUID roundId, UUID actorId) {
        RoundRow round = lockRound(roundId);
        if (round.status() != RoundStatus.CANCELLED) {
            throw new IllegalStateException("only a CANCELLED round can refund bets");
        }
        var bets = jdbc.query("SELECT id, round_id, player_id, amount, currency, status, hold_id, accepted_sequence, "
                + "cash_out_sequence, cash_out_multiplier, payout_amount FROM bets WHERE tenant_id = :tenantId "
                + "AND round_id = :roundId AND status = 'ACCEPTED' FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("roundId", roundId), this::betRow);
        for (BetRow bet : bets) {
            wallet.release(tenantId, bet.holdId(), actorId, "bet-refund:" + bet.id());
            jdbc.update("UPDATE bets SET status = 'CANCELLED', settled_at = CURRENT_TIMESTAMP WHERE id = :id",
                    new MapSqlParameterSource("id", bet.id()));
            publish(bet.withStatus(BetStatus.CANCELLED), round.eventSequence(), bet.amount());
        }
        return bets.size();
    }

    private void publish(BetRow bet, long sequence, BigDecimal payout) {
        BettingEvent event = new BettingEvent(bet.id(), bet.roundId(), bet.playerId(), bet.status(), bet.amount(),
                payout, bet.multiplier(), sequence, Instant.now(clock));
        outbox.append("BET", event.betId(), "bet." + event.status().name().toLowerCase() + ".v1", 1,
                event.roundSequence(), EventPayload.bet(event), event.roundId(), null);
    }

    private RoundRow lockRound(UUID roundId) {
        return jdbc.queryForObject("SELECT id, status, event_sequence, last_multiplier FROM crash_rounds WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", roundId), (rs, n) -> new RoundRow(rs.getObject("id", UUID.class),
                        RoundStatus.valueOf(rs.getString("status")), rs.getLong("event_sequence"), rs.getBigDecimal("last_multiplier")));
    }

    private BetRow lockBet(UUID tenantId, UUID playerId, UUID betId) {
        return jdbc.queryForObject("SELECT id, round_id, player_id, amount, currency, status, hold_id, accepted_sequence, "
                + "cash_out_sequence, cash_out_multiplier, payout_amount FROM bets WHERE tenant_id = :tenantId "
                + "AND player_id = :playerId AND id = :id FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId).addValue("id", betId), this::betRow);
    }

    private BetRow findByPlacementKey(UUID tenantId, String key) {
        return findBet("SELECT id, round_id, player_id, amount, currency, status, hold_id, accepted_sequence, cash_out_sequence, "
                + "cash_out_multiplier, payout_amount FROM bets WHERE tenant_id = :tenantId AND idempotency_key = :key",
                tenantId, key);
    }

    private BetRow findBetById(UUID tenantId, UUID playerId, UUID betId) {
        return jdbc.query("SELECT id, round_id, player_id, amount, currency, status, hold_id, accepted_sequence, cash_out_sequence, "
                + "cash_out_multiplier, payout_amount FROM bets WHERE tenant_id = :tenantId AND player_id = :playerId AND id = :id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId).addValue("id", betId), rows -> {
                    return rows.next() ? betRow(rows, 0) : null;
                });
    }

    private CashOutRow findCashOut(UUID tenantId, String key) {
        return jdbc.query("SELECT c.bet_id, b.round_id, b.player_id, b.amount, b.currency, c.multiplier, c.payout_amount, c.round_sequence "
                + "FROM bet_cash_outs c JOIN bets b ON b.id = c.bet_id WHERE c.tenant_id = :tenantId AND c.idempotency_key = :key",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("key", key), rows -> {
                    if (!rows.next()) return null;
                    return new CashOutRow(rows.getObject("bet_id", UUID.class), rows.getObject("round_id", UUID.class),
                            rows.getObject("player_id", UUID.class), rows.getBigDecimal("amount"), rows.getString("currency"),
                            rows.getBigDecimal("multiplier"), rows.getBigDecimal("payout_amount"), rows.getLong("round_sequence"));
                });
    }

    private BetRow findBet(String sql, UUID tenantId, String key) {
        return jdbc.query(sql, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("key", key), rows -> {
            return rows.next() ? betRow(rows, 0) : null;
        });
    }

    private BetRow betRow(ResultSet rs, int ignored) throws SQLException {
        return new BetRow(rs.getObject("id", UUID.class), rs.getObject("round_id", UUID.class), rs.getObject("player_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("currency").trim(), BetStatus.valueOf(rs.getString("status")), rs.getObject("hold_id", UUID.class),
                rs.getLong("accepted_sequence"), nullableLong(rs, "cash_out_sequence"), rs.getBigDecimal("cash_out_multiplier"), rs.getBigDecimal("payout_amount"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private MapSqlParameterSource params(PlaceBetCommand c) {
        return new MapSqlParameterSource().addValue("id", c.betId()).addValue("tenantId", c.tenantId()).addValue("playerId", c.playerId())
                .addValue("roundId", c.roundId()).addValue("amount", c.amount()).addValue("currency", c.currency().toUpperCase())
                .addValue("idempotencyKey", c.idempotencyKey());
    }

    private BetResult result(BetRow b, boolean duplicate) {
        return new BetResult(b.id(), b.roundId(), b.playerId(), b.amount(), b.payoutAmount(), b.multiplier(), b.status(),
                b.cashOutSequence() == null ? b.acceptedSequence() : b.cashOutSequence(), duplicate);
    }

    private record RoundRow(UUID id, RoundStatus status, long eventSequence, BigDecimal lastMultiplier) { }
    private record BetRow(UUID id, UUID roundId, UUID playerId, BigDecimal amount, String currency, BetStatus status, UUID holdId,
            long acceptedSequence, Long cashOutSequence, BigDecimal multiplier, BigDecimal payoutAmount) {
        BetRow withCashOut(long sequence, BigDecimal multiplier, BigDecimal payout) {
            return new BetRow(id, roundId, playerId, amount, currency, BetStatus.CASHED_OUT, holdId, acceptedSequence, sequence, multiplier, payout);
        }
        BetRow withStatus(BetStatus next) { return new BetRow(id, roundId, playerId, amount, currency, next, holdId, acceptedSequence, cashOutSequence, multiplier, payoutAmount); }
    }
    private record CashOutRow(UUID betId, UUID roundId, UUID playerId, BigDecimal amount, String currency,
            BigDecimal multiplier, BigDecimal payout, long sequence) {
        BetResult result(boolean duplicate) { return new BetResult(betId, roundId, playerId, amount, payout, multiplier, BetStatus.CASHED_OUT, sequence, duplicate); }
    }
}
