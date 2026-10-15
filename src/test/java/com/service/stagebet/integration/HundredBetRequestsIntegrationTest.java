package com.service.stagebet.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.service.stagebet.game.application.CrashRoundEngine;
import com.service.stagebet.wallet.application.ReconciliationResult;
import com.service.stagebet.wallet.application.WalletBalance;
import com.service.stagebet.wallet.application.WalletCommand;
import com.service.stagebet.wallet.application.WalletOperationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "stagebet.rate-limit.max-requests=1000")
class HundredBetRequestsIntegrationTest {

    private static final int REQUEST_COUNT = 100;
    private static final int WORKER_COUNT = 20;
    private static final int FUNDED_BETS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CrashRoundEngine roundEngine;

    @Autowired
    private WalletOperationService wallet;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void hundredBetRequestsWithTwentyWorkersCannotOverdrawAndAcceptedBetsCanCashOut() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        wallet.deposit(new WalletCommand(tenantId, playerId, UUID.randomUUID(), new BigDecimal("100.00"),
                "USD", "e2e-deposit-" + roundId, null));
        roundEngine.scheduleRound(roundId);
        roundEngine.openRound(roundId);

        List<BetAttempt> attempts = placeConcurrentBets(tenantId, playerId, roundId);
        List<UUID> acceptedBetIds = new ArrayList<>();
        int insufficientFunds = 0;
        for (BetAttempt attempt : attempts) {
            int status = attempt.response().get(60, TimeUnit.SECONDS);
            if (status == 200) {
                acceptedBetIds.add(attempt.betId());
            } else {
                assertEquals(422, status, "unexpected bet response for " + attempt.betId());
                insufficientFunds++;
            }
        }
        assertEquals(FUNDED_BETS, acceptedBetIds.size());
        assertEquals(REQUEST_COUNT - FUNDED_BETS, insufficientFunds);

        WalletBalance reserved = wallet.getBalance(tenantId, playerId, "USD");
        assertEquals(new BigDecimal("0.00"), reserved.availableBalance());
        assertEquals(new BigDecimal("100.00"), reserved.reservedBalance());
        assertTrue(wallet.reconcile(tenantId, playerId, "USD").balanced());

        roundEngine.startRound(roundId);
        for (UUID betId : acceptedBetIds) {
            int status = mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/bets/{betId}/cash-out",
                            tenantId, playerId, betId)
                    .with(user("e2e-player"))
                    .header("X-Idempotency-Key", "e2e-cashout-" + betId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedRoundSequence\":2}"))
                    .andReturn().getResponse().getStatus();
            assertEquals(200, status, "cash-out failed for " + betId);
        }

        WalletBalance afterCashOut = wallet.getBalance(tenantId, playerId, "USD");
        ReconciliationResult reconciliation = wallet.reconcile(tenantId, playerId, "USD");
        assertEquals(new BigDecimal("100.00"), afterCashOut.availableBalance());
        assertEquals(new BigDecimal("0.00"), afterCashOut.reservedBalance());
        assertTrue(reconciliation.balanced());
        assertEquals(FUNDED_BETS, countCashedOutBets(tenantId, roundId));
    }

    private List<BetAttempt> placeConcurrentBets(UUID tenantId, UUID playerId, UUID roundId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<BetAttempt> attempts = new ArrayList<>();
        try {
            for (int index = 0; index < REQUEST_COUNT; index++) {
                UUID betId = UUID.randomUUID();
                Future<Integer> response = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets",
                                    tenantId, playerId, roundId)
                            .with(user("e2e-player"))
                            .header("X-Idempotency-Key", "e2e-bet-" + betId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"betId\":\"" + betId
                                    + "\",\"amount\":\"10.00\",\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                            .andReturn().getResponse().getStatus();
                });
                attempts.add(new BetAttempt(betId, response));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();
            for (BetAttempt attempt : attempts) {
                attempt.response().get(60, TimeUnit.SECONDS);
            }
            return attempts;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int countCashedOutBets(UUID tenantId, UUID roundId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bets WHERE tenant_id = :tenantId AND round_id = :roundId "
                        + "AND status = 'CASHED_OUT'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("roundId", roundId), Integer.class);
    }

    private record BetAttempt(UUID betId, Future<Integer> response) {
    }
}
