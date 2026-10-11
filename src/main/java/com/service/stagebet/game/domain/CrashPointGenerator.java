package com.service.stagebet.game.domain;

import java.math.BigDecimal;
import java.util.UUID;

public interface CrashPointGenerator {

    GeneratedCrashPoint generate(String serverSeed, UUID roundId);

    record GeneratedCrashPoint(BigDecimal multiplier, String serverSeedHash) {
    }
}
