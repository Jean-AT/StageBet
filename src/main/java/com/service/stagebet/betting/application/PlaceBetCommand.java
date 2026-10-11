package com.service.stagebet.betting.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PlaceBetCommand(UUID tenantId, UUID playerId, UUID roundId, UUID betId,
        BigDecimal amount, String currency, long expectedRoundSequence, String idempotencyKey, UUID actorId) {

    public PlaceBetCommand {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(roundId);
        Objects.requireNonNull(betId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(idempotencyKey);
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1 to 128 characters");
        }
    }
}
