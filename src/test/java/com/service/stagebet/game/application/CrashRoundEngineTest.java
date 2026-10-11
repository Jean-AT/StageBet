package com.service.stagebet.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.service.stagebet.game.domain.CrashPointGenerator;
import com.service.stagebet.game.domain.CrashRound;
import com.service.stagebet.game.domain.RoundEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrashRoundEngineTest {

    @Test
    void ticksWithServerTimeAndPublishesOnlyAuthoritativeEvents() {
        InMemoryRoundStore store = new InMemoryRoundStore();
        RecordingPublisher publisher = new RecordingPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:05Z"), ZoneOffset.UTC);
        CrashPointGenerator generator = (seed, roundId) -> new CrashPointGenerator.GeneratedCrashPoint(
                new BigDecimal("2.00"), "hash");
        CrashRoundEngine engine = new CrashRoundEngine(store, generator, publisher, clock, () -> "seed");
        UUID roundId = UUID.randomUUID();

        engine.scheduleRound(roundId);
        engine.openRound(roundId);
        engine.startRound(roundId);
        RoundEvent event = engine.tickRound(roundId);

        assertEquals(3, event.sequence());
        assertEquals(3, publisher.events.size());
        assertEquals(new BigDecimal("1.00"), event.multiplier());
        assertEquals(3, store.findById(roundId).orElseThrow().snapshot().eventSequence());
        assertNull(publisher.revealedSeeds.get(2));

        engine.crashRound(roundId);

        assertEquals("seed", publisher.revealedSeeds.get(3));
    }

    private static class InMemoryRoundStore implements CrashRoundStore {

        private final Map<UUID, CrashRound> rounds = new HashMap<>();

        @Override
        public void save(CrashRound round) {
            rounds.put(round.id(), round);
        }

        @Override
        public Optional<CrashRound> findById(UUID roundId) {
            return Optional.ofNullable(rounds.get(roundId));
        }
    }

    private static class RecordingPublisher implements RoundEventPublisher {

        private final java.util.List<RoundEvent> events = new java.util.ArrayList<>();
        private final java.util.List<String> revealedSeeds = new java.util.ArrayList<>();

        @Override
        public void publish(RoundEvent event, String serverSeedHash, String revealedServerSeed) {
            events.add(event);
            revealedSeeds.add(revealedServerSeed);
        }
    }
}
