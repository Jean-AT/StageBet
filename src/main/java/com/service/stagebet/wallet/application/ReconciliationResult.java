package com.service.stagebet.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationResult(
        UUID walletId,
        BigDecimal availableBalance,
        BigDecimal ledgerAvailableBalance,
        BigDecimal reservedBalance,
        BigDecimal activeHoldBalance,
        boolean balanced) {
}
