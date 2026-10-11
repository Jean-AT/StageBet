package com.service.stagebet.betting.application;

import java.util.Objects;
import java.util.UUID;

public record CashOutCommand(UUID tenantId, UUID playerId, UUID betId,
        long expectedRoundSequence, String idempotencyKey, UUID actorId) {

    public CashOutCommand {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(betId);
        Objects.requireNonNull(idempotencyKey);
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1 to 128 characters");
        }
    }
}
