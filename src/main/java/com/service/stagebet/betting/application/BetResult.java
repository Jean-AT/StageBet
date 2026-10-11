package com.service.stagebet.betting.application;

import com.service.stagebet.betting.domain.BetStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record BetResult(UUID betId, UUID roundId, UUID playerId, BigDecimal amount,
        BigDecimal payoutAmount, BigDecimal multiplier, BetStatus status, long roundSequence, boolean duplicate) {
}
