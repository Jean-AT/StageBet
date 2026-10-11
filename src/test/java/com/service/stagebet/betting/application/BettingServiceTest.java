package com.service.stagebet.betting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.service.stagebet.game.application.CrashRoundEngine;
import com.service.stagebet.betting.domain.BetStatus;
import com.service.stagebet.wallet.application.WalletCommand;
import com.service.stagebet.wallet.application.WalletOperationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BettingServiceTest {

    @Autowired
    private BettingService bettingService;

    @Autowired
    private CrashRoundEngine roundEngine;

    @Autowired
    private WalletOperationService walletOperationService;

    @Test
    void reservesOnceAndPaysAValidCashOut() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID betId = UUID.randomUUID();
        walletOperationService.deposit(walletCommand(tenantId, playerId, UUID.randomUUID(), "100.00", "bet-test-deposit"));
        roundEngine.scheduleRound(roundId);
        roundEngine.openRound(roundId);

        PlaceBetCommand placeBet = new PlaceBetCommand(tenantId, playerId, roundId, betId,
                new BigDecimal("10.00"), "USD", 1, "bet-test-place", null);
        BetResult accepted = bettingService.placeBet(placeBet);
        BetResult duplicate = bettingService.placeBet(placeBet);

        assertEquals(accepted.betId(), duplicate.betId());
        assertTrue(duplicate.duplicate());

        roundEngine.startRound(roundId);
        BetResult cashedOut = bettingService.cashOut(new CashOutCommand(tenantId, playerId, betId, 2,
                "bet-test-cashout", null));

        assertEquals(BetStatus.CASHED_OUT, cashedOut.status());
        assertEquals(new BigDecimal("10.00"), cashedOut.payoutAmount());
    }

    @Test
    void rejectsBetsAfterTheRoundLeavesTheOpenPhase() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        roundEngine.scheduleRound(roundId);
        roundEngine.openRound(roundId);
        roundEngine.startRound(roundId);

        assertThrows(IllegalStateException.class, () -> bettingService.placeBet(new PlaceBetCommand(tenantId, playerId,
                roundId, UUID.randomUUID(), new BigDecimal("1.00"), "USD", 2, "bet-test-late", null)));
    }

    private WalletCommand walletCommand(UUID tenantId, UUID playerId, UUID referenceId, String amount, String key) {
        return new WalletCommand(tenantId, playerId, referenceId, new BigDecimal(amount), "USD", key, null);
    }
}
