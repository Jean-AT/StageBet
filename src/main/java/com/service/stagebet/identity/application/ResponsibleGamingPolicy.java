package com.service.stagebet.identity.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface ResponsibleGamingPolicy {

    void validateAndRecordBet(UUID tenantId, UUID playerId, BigDecimal amount);

    static ResponsibleGamingPolicy allowAll() {
        return (tenantId, playerId, amount) -> {
        };
    }
}
