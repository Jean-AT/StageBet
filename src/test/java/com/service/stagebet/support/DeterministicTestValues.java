package com.service.stagebet.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class DeterministicTestValues {

    public static final Instant TEST_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    public static final Clock TEST_CLOCK = Clock.fixed(TEST_INSTANT, ZoneOffset.UTC);

    private DeterministicTestValues() {
    }

    public static Supplier<UUID> uuidSupplier() {
        AtomicLong sequence = new AtomicLong(1);
        return () -> new UUID(0L, sequence.getAndIncrement());
    }

    public static Supplier<String> seedSupplier() {
        AtomicLong sequence = new AtomicLong(1);
        return () -> "test-seed-" + sequence.getAndIncrement();
    }
}
