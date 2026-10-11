package com.service.stagebet.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrashRoundTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void advancesThroughTheAuthoritativeLifecycleWithMonotonicSequences() {
        CrashRound round = round();

        RoundEvent opened = round.open(START.plusSeconds(1));
        RoundEvent started = round.start(START.plusSeconds(2));
        RoundEvent ticked = round.tick(new BigDecimal("1.20"), START.plusSeconds(3));
        RoundEvent crashed = round.crash(START.plusSeconds(4));
        RoundEvent settled = round.settle(START.plusSeconds(5));

        assertEquals(RoundStatus.OPEN, opened.status());
        assertEquals(1, opened.sequence());
        assertEquals(2, started.sequence());
        assertEquals(3, ticked.sequence());
        assertEquals(4, crashed.sequence());
        assertEquals(5, settled.sequence());
        assertEquals(RoundStatus.SETTLED, round.status());
        assertEquals(new BigDecimal("2.00"), round.lastMultiplier());
    }

    @Test
    void rejectsADecreasingMultiplierAndInvalidTransition() {
        CrashRound round = round();
        round.open(START.plusSeconds(1));
        round.start(START.plusSeconds(2));
        round.tick(new BigDecimal("1.20"), START.plusSeconds(3));

        assertThrows(IllegalArgumentException.class,
                () -> round.tick(new BigDecimal("1.10"), START.plusSeconds(4)));
        assertThrows(IllegalStateException.class, () -> round.settle(START.plusSeconds(4)));
    }

    private CrashRound round() {
        return CrashRound.schedule(UUID.randomUUID(), "server-seed", "hash",
                new BigDecimal("2.00"), START);
    }
}
