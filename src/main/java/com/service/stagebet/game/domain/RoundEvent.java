package com.service.stagebet.game.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoundEvent(
        UUID roundId,
        long sequence,
        RoundStatus status,
        BigDecimal multiplier,
        Instant occurredAt) {
}
