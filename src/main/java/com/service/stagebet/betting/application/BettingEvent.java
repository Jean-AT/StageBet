package com.service.stagebet.betting.application;

import com.service.stagebet.betting.domain.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BettingEvent(UUID betId, UUID roundId, UUID playerId, BetStatus status,
        BigDecimal amount, BigDecimal payoutAmount, BigDecimal multiplier, long roundSequence, Instant occurredAt) {
}
