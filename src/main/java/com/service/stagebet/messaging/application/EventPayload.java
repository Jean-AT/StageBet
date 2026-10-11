package com.service.stagebet.messaging.application;

import com.service.stagebet.game.domain.RoundEvent;
import com.service.stagebet.betting.application.BettingEvent;
import java.math.BigDecimal;

public final class EventPayload {

    private EventPayload() {
    }

    public static String round(RoundEvent event, String serverSeedHash, String revealedServerSeed) {
        return "{\"roundId\":\"" + event.roundId() + "\",\"sequence\":" + event.sequence()
                + ",\"status\":\"" + event.status() + "\",\"multiplier\":" + decimal(event.multiplier())
                + ",\"serverSeedHash\":\"" + serverSeedHash + "\""
                + (revealedServerSeed == null ? "" : ",\"serverSeed\":\"" + revealedServerSeed + "\"")
                + ",\"occurredAt\":\"" + event.occurredAt() + "\"}";
    }

    public static String bet(BettingEvent event) {
        return "{\"betId\":\"" + event.betId() + "\",\"roundId\":\"" + event.roundId()
                + "\",\"playerId\":\"" + event.playerId() + "\",\"status\":\"" + event.status()
                + "\",\"amount\":" + decimal(event.amount()) + ",\"payoutAmount\":" + nullable(event.payoutAmount())
                + ",\"multiplier\":" + nullable(event.multiplier()) + ",\"roundSequence\":" + event.roundSequence()
                + ",\"occurredAt\":\"" + event.occurredAt() + "\"}";
    }

    private static String decimal(BigDecimal value) {
        return value.toPlainString();
    }

    private static String nullable(BigDecimal value) {
        return value == null ? "null" : decimal(value);
    }
}
