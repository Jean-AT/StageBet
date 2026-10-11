package com.service.stagebet.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletOperationResult(
        UUID referenceId,
        UUID walletId,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String currency,
        boolean duplicate) {
}
