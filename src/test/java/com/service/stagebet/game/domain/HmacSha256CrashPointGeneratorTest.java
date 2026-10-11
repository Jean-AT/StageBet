package com.service.stagebet.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HmacSha256CrashPointGeneratorTest {

    private final HmacSha256CrashPointGenerator generator = new HmacSha256CrashPointGenerator();

    @Test
    void generatesTheSameResultForTheSameSeedAndRound() {
        UUID roundId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        CrashPointGenerator.GeneratedCrashPoint first = generator.generate("server-seed", roundId);
        CrashPointGenerator.GeneratedCrashPoint second = generator.generate("server-seed", roundId);

        assertEquals(first, second);
        assertEquals(64, first.serverSeedHash().length());
    }

    @Test
    void publishesADifferentCommitWhenTheServerSeedChanges() {
        CrashPointGenerator.GeneratedCrashPoint first = generator.generate("server-seed",
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        CrashPointGenerator.GeneratedCrashPoint second = generator.generate("different-server-seed",
                UUID.fromString("22222222-2222-2222-2222-222222222222"));

        org.junit.jupiter.api.Assertions.assertNotEquals(first.serverSeedHash(), second.serverSeedHash());
    }
}
