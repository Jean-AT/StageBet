package com.service.stagebet.game.application;

import com.service.stagebet.game.domain.CrashPointGenerator;
import com.service.stagebet.game.domain.CrashRound;
import com.service.stagebet.game.domain.CrashRoundSnapshot;
import com.service.stagebet.game.domain.RoundEvent;
import com.service.stagebet.game.domain.RoundStatus;
import com.service.stagebet.messaging.application.EventPayload;
import com.service.stagebet.messaging.application.OutboxEventStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrashRoundEngine {

    private static final BigDecimal BASE_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal GROWTH_PER_SECOND = new BigDecimal("0.20");

    private final CrashRoundStore roundStore;
    private final CrashPointGenerator crashPointGenerator;
    private final RoundEventPublisher eventPublisher;
    private final Clock clock;
    private final Supplier<String> serverSeedSupplier;
    private final OutboxEventStore outbox;
    private final Map<UUID, ReentrantLock> roundLocks = new ConcurrentHashMap<>();

    public CrashRoundEngine(CrashRoundStore roundStore, CrashPointGenerator crashPointGenerator,
            RoundEventPublisher eventPublisher) {
        this(roundStore, crashPointGenerator, eventPublisher, Clock.systemUTC(), CrashRoundEngine::generateServerSeed,
                OutboxEventStore.noop());
    }

    @Autowired
    public CrashRoundEngine(CrashRoundStore roundStore, CrashPointGenerator crashPointGenerator,
            RoundEventPublisher eventPublisher, OutboxEventStore outbox) {
        this(roundStore, crashPointGenerator, eventPublisher, Clock.systemUTC(), CrashRoundEngine::generateServerSeed, outbox);
    }

    CrashRoundEngine(CrashRoundStore roundStore, CrashPointGenerator crashPointGenerator,
            RoundEventPublisher eventPublisher, Clock clock, Supplier<String> serverSeedSupplier) {
        this(roundStore, crashPointGenerator, eventPublisher, clock, serverSeedSupplier, OutboxEventStore.noop());
    }

    CrashRoundEngine(CrashRoundStore roundStore, CrashPointGenerator crashPointGenerator,
            RoundEventPublisher eventPublisher, Clock clock, Supplier<String> serverSeedSupplier,
            OutboxEventStore outbox) {
        this.roundStore = roundStore;
        this.crashPointGenerator = crashPointGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.serverSeedSupplier = serverSeedSupplier;
        this.outbox = outbox;
    }

    @Transactional
    public CrashRoundSnapshot scheduleRound(UUID roundId) {
        String serverSeed = serverSeedSupplier.get();
        CrashPointGenerator.GeneratedCrashPoint crashPoint = crashPointGenerator.generate(serverSeed, roundId);
        CrashRound round = CrashRound.schedule(roundId, serverSeed, crashPoint.serverSeedHash(),
                crashPoint.multiplier(), clock.instant());
        roundStore.save(round);
        return round.snapshot();
    }

    @Transactional
    public RoundEvent openRound(UUID roundId) {
        return mutate(roundId, round -> round.open(clock.instant()));
    }

    @Transactional
    public RoundEvent startRound(UUID roundId) {
        return mutate(roundId, round -> round.start(clock.instant()));
    }

    @Transactional
    public RoundEvent tickRound(UUID roundId) {
        return mutate(roundId, round -> {
            if (round.status() != RoundStatus.RUNNING) {
                throw new IllegalStateException("round must be RUNNING but was " + round.status());
            }
            Duration elapsed = Duration.between(round.snapshot().startedAt(), clock.instant());
            BigDecimal multiplier = BASE_MULTIPLIER.add(GROWTH_PER_SECOND
                    .multiply(BigDecimal.valueOf(elapsed.toMillis()))
                    .divide(new BigDecimal("1000"), 2, RoundingMode.DOWN));
            return round.tick(multiplier, clock.instant());
        });
    }

    @Transactional
    public RoundEvent crashRound(UUID roundId) {
        return mutate(roundId, round -> round.crash(clock.instant()));
    }

    @Transactional
    public RoundEvent settleRound(UUID roundId) {
        return mutate(roundId, round -> round.settle(clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<CrashRoundSnapshot> findRound(UUID roundId) {
        return roundStore.findById(roundId).map(CrashRound::snapshot);
    }

    private RoundEvent mutate(UUID roundId, java.util.function.Function<CrashRound, RoundEvent> mutation) {
        ReentrantLock lock = roundLocks.computeIfAbsent(roundId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            CrashRound round = roundStore.findByIdForUpdate(roundId)
                    .orElseThrow(() -> new IllegalArgumentException("round does not exist: " + roundId));
            RoundEvent event = mutation.apply(round);
            roundStore.save(round);
            String revealedServerSeed = event.status() == RoundStatus.CRASHED || event.status() == RoundStatus.SETTLED
                    ? round.serverSeed() : null;
            outbox.append("CRASH_ROUND", event.roundId(), "round." + event.status().name().toLowerCase() + ".v1", 1,
                    event.sequence(), EventPayload.round(event, round.serverSeedHash(), revealedServerSeed),
                    event.roundId(), null);
            eventPublisher.publish(event, round.serverSeedHash(), revealedServerSeed);
            return event;
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                roundLocks.remove(roundId, lock);
            }
        }
    }

    private static String generateServerSeed() {
        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        return java.util.HexFormat.of().formatHex(seed);
    }
}
