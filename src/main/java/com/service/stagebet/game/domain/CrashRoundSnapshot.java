package com.service.stagebet.game.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CrashRoundSnapshot(
        UUID id,
        RoundStatus status,
        String serverSeed,
        String serverSeedHash,
        BigDecimal crashMultiplier,
        BigDecimal lastMultiplier,
        long eventSequence,
        Instant scheduledAt,
        Instant openedAt,
        Instant startedAt,
        Instant crashedAt,
        Instant settledAt,
        long version) {
}
