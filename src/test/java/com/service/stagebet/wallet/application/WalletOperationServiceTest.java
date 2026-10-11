package com.service.stagebet.wallet.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WalletOperationServiceTest {

    @Autowired
    private WalletOperationService walletOperationService;

    @Test
    void depositsAndReservesFundsWithIdempotentCommands() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID depositId = UUID.randomUUID();
        WalletCommand deposit = command(tenantId, playerId, depositId, "100.00", "deposit-1");

        WalletOperationResult deposited = walletOperationService.deposit(deposit);
        WalletOperationResult duplicateDeposit = walletOperationService.deposit(deposit);

        assertEquals(new BigDecimal("100.00"), deposited.availableBalance());
        assertEquals(deposited.walletId(), duplicateDeposit.walletId());
        assertEquals(true, duplicateDeposit.duplicate());

        WalletOperationResult reserved = walletOperationService.reserve(
                command(tenantId, playerId, UUID.randomUUID(), "40.00", "reserve-1"));

        assertEquals(new BigDecimal("60.00"), reserved.availableBalance());
        assertEquals(new BigDecimal("40.00"), reserved.reservedBalance());
    }

    @Test
    void rejectsReservationWhenAvailableBalanceIsInsufficient() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        walletOperationService.deposit(command(tenantId, playerId, UUID.randomUUID(), "10.00", "deposit-2"));

        assertThrows(InsufficientFundsException.class, () -> walletOperationService.reserve(
                command(tenantId, playerId, UUID.randomUUID(), "10.01", "reserve-2")));
    }

    @Test
    void acceptsExactlyTenConcurrentReservationsFromOneHundredCredits() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        walletOperationService.deposit(command(tenantId, playerId, UUID.randomUUID(), "100.00", "deposit-concurrent"));

        int requestCount = 20;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                String key = "reserve-concurrent-" + index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        walletOperationService.reserve(command(tenantId, playerId, UUID.randomUUID(), "10.00", key));
                        return true;
                    } catch (InsufficientFundsException exception) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();

            long successfulReservations = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulReservations++;
                }
            }

            WalletBalance balance = walletOperationService.getBalance(tenantId, playerId, "USD");
            ReconciliationResult reconciliation = walletOperationService.reconcile(tenantId, playerId, "USD");

            assertEquals(10, successfulReservations);
            assertEquals(new BigDecimal("0.00"), balance.availableBalance());
            assertEquals(new BigDecimal("100.00"), balance.reservedBalance());
            assertTrue(reconciliation.balanced());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void makesReleaseCaptureAndPayoutIdempotent() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        walletOperationService.deposit(command(tenantId, playerId, UUID.randomUUID(), "100.00", "deposit-lifecycle"));

        UUID holdId = UUID.randomUUID();
        WalletOperationResult reserved = walletOperationService.reserve(
                command(tenantId, playerId, holdId, "20.00", "reserve-lifecycle"));
        WalletOperationResult released = walletOperationService.release(tenantId, holdId, null, "release-lifecycle");
        WalletOperationResult duplicateRelease = walletOperationService.release(tenantId, holdId, null, "release-lifecycle");

        assertEquals(reserved.walletId(), released.walletId());
        assertTrue(duplicateRelease.duplicate());
        assertEquals(new BigDecimal("100.00"), released.availableBalance());

        UUID capturedHoldId = UUID.randomUUID();
        walletOperationService.reserve(command(tenantId, playerId, capturedHoldId, "15.00", "reserve-capture"));
        WalletOperationResult captured = walletOperationService.capture(tenantId, capturedHoldId, null, "capture-lifecycle");
        WalletOperationResult duplicateCapture = walletOperationService.capture(tenantId, capturedHoldId, null, "capture-lifecycle");
        assertTrue(duplicateCapture.duplicate());
        assertEquals(captured.walletId(), duplicateCapture.walletId());

        WalletOperationResult payout = walletOperationService.payout(
                command(tenantId, playerId, UUID.randomUUID(), "5.00", "payout-lifecycle"));
        WalletOperationResult duplicatePayout = walletOperationService.payout(
                command(tenantId, playerId, payout.referenceId(), "5.00", "payout-lifecycle"));
        assertTrue(duplicatePayout.duplicate());
        assertEquals(payout.walletId(), duplicatePayout.walletId());
    }

    private WalletCommand command(UUID tenantId, UUID playerId, UUID referenceId, String amount, String key) {
        return new WalletCommand(tenantId, playerId, referenceId, new BigDecimal(amount), "USD", key, null);
    }
}
