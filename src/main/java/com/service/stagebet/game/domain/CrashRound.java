package com.service.stagebet.game.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CrashRound {

    private static final BigDecimal ONE = new BigDecimal("1.00");
    private static final BigDecimal MINIMUM_CRASH_MULTIPLIER = new BigDecimal("1.01");

    private final UUID id;
    private final String serverSeed;
    private final String serverSeedHash;
    private final BigDecimal crashMultiplier;
    private final Instant scheduledAt;
    private RoundStatus status;
    private BigDecimal lastMultiplier;
    private long eventSequence;
    private Instant openedAt;
    private Instant startedAt;
    private Instant crashedAt;
    private Instant settledAt;
    private long version;

    private CrashRound(UUID id, RoundStatus status, String serverSeed, String serverSeedHash,
            BigDecimal crashMultiplier, BigDecimal lastMultiplier, long eventSequence, Instant scheduledAt,
            Instant openedAt, Instant startedAt, Instant crashedAt, Instant settledAt, long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.serverSeed = Objects.requireNonNull(serverSeed, "serverSeed must not be null");
        this.serverSeedHash = Objects.requireNonNull(serverSeedHash, "serverSeedHash must not be null");
        this.crashMultiplier = normalizeMultiplier(crashMultiplier);
        this.lastMultiplier = normalizeMultiplier(lastMultiplier);
        this.eventSequence = eventSequence;
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        this.openedAt = openedAt;
        this.startedAt = startedAt;
        this.crashedAt = crashedAt;
        this.settledAt = settledAt;
        this.version = version;
        if (this.lastMultiplier.compareTo(this.crashMultiplier) > 0) {
            throw new IllegalArgumentException("last multiplier cannot exceed crash multiplier");
        }
    }

    public static CrashRound schedule(UUID id, String serverSeed, String serverSeedHash,
            BigDecimal crashMultiplier, Instant scheduledAt) {
        if (normalizeMultiplier(crashMultiplier).compareTo(MINIMUM_CRASH_MULTIPLIER) < 0) {
            throw new IllegalArgumentException("crash multiplier must be at least 1.01");
        }
        return new CrashRound(id, RoundStatus.SCHEDULED, serverSeed, serverSeedHash,
                crashMultiplier, ONE, 0, scheduledAt, null, null, null, null, 0);
    }

    public static CrashRound restore(CrashRoundSnapshot snapshot) {
        return new CrashRound(snapshot.id(), snapshot.status(), snapshot.serverSeed(), snapshot.serverSeedHash(),
                snapshot.crashMultiplier(), snapshot.lastMultiplier(), snapshot.eventSequence(), snapshot.scheduledAt(),
                snapshot.openedAt(), snapshot.startedAt(), snapshot.crashedAt(), snapshot.settledAt(), snapshot.version());
    }

    public RoundEvent open(Instant occurredAt) {
        requireStatus(RoundStatus.SCHEDULED);
        openedAt = requireAfterSchedule(occurredAt);
        status = RoundStatus.OPEN;
        return nextEvent(occurredAt);
    }

    public RoundEvent start(Instant occurredAt) {
        requireStatus(RoundStatus.OPEN);
        startedAt = requireAfter(openedAt, occurredAt);
        status = RoundStatus.RUNNING;
        return nextEvent(occurredAt);
    }

    public RoundEvent tick(BigDecimal multiplier, Instant occurredAt) {
        requireStatus(RoundStatus.RUNNING);
        BigDecimal normalizedMultiplier = normalizeMultiplier(multiplier);
        if (normalizedMultiplier.compareTo(lastMultiplier) < 0) {
            throw new IllegalArgumentException("multiplier must be monotonic");
        }
        if (normalizedMultiplier.compareTo(crashMultiplier) >= 0) {
            return crash(occurredAt);
        }
        lastMultiplier = normalizedMultiplier;
        return nextEvent(occurredAt);
    }

    public RoundEvent crash(Instant occurredAt) {
        requireStatus(RoundStatus.RUNNING);
        crashedAt = requireAfter(startedAt, occurredAt);
        lastMultiplier = crashMultiplier;
        status = RoundStatus.CRASHED;
        return nextEvent(occurredAt);
    }

    public RoundEvent settle(Instant occurredAt) {
        requireStatus(RoundStatus.CRASHED);
        settledAt = requireAfter(crashedAt, occurredAt);
        status = RoundStatus.SETTLED;
        return nextEvent(occurredAt);
    }

    public RoundEvent cancel(Instant occurredAt) {
        if (status != RoundStatus.SCHEDULED && status != RoundStatus.OPEN) {
            throw new IllegalStateException("only scheduled or open rounds can be cancelled");
        }
        status = RoundStatus.CANCELLED;
        return nextEvent(occurredAt);
    }

    public CrashRoundSnapshot snapshot() {
        return new CrashRoundSnapshot(id, status, serverSeed, serverSeedHash, crashMultiplier, lastMultiplier,
                eventSequence, scheduledAt, openedAt, startedAt, crashedAt, settledAt, version);
    }

    public UUID id() {
        return id;
    }

    public RoundStatus status() {
        return status;
    }

    public BigDecimal crashMultiplier() {
        return crashMultiplier;
    }

    public BigDecimal lastMultiplier() {
        return lastMultiplier;
    }

    public String serverSeedHash() {
        return serverSeedHash;
    }

    public String serverSeed() {
        return serverSeed;
    }

    private RoundEvent nextEvent(Instant occurredAt) {
        eventSequence++;
        version++;
        return new RoundEvent(id, eventSequence, status, lastMultiplier, occurredAt);
    }

    private Instant requireAfterSchedule(Instant occurredAt) {
        return requireAfter(scheduledAt, occurredAt);
    }

    private Instant requireAfter(Instant previous, Instant current) {
        Objects.requireNonNull(current, "occurredAt must not be null");
        if (current.isBefore(previous)) {
            throw new IllegalArgumentException("event time cannot move backwards");
        }
        return current;
    }

    private void requireStatus(RoundStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("round must be " + expected + " but was " + status);
        }
    }

    private static BigDecimal normalizeMultiplier(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier must not be null");
        return multiplier.setScale(2, RoundingMode.UNNECESSARY);
    }
}
