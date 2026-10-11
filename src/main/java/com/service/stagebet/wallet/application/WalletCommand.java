package com.service.stagebet.wallet.application;

import com.service.stagebet.wallet.domain.Money;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WalletCommand(
        UUID tenantId,
        UUID playerId,
        UUID referenceId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        UUID actorId) {

    public WalletCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1 to 128 characters");
        }

        new Money(amount, currency);
    }

    public Money money() {
        return new Money(amount, currency);
    }
}
