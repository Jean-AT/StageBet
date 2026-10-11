package com.service.stagebet.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.service.stagebet.game.domain.RoundStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CrashRoundPersistenceTest {

    @Autowired
    private CrashRoundEngine crashRoundEngine;

    @Test
    void recoversAScheduledRoundFromPostgres() {
        UUID roundId = UUID.randomUUID();

        crashRoundEngine.scheduleRound(roundId);

        assertEquals(RoundStatus.SCHEDULED, crashRoundEngine.findRound(roundId).orElseThrow().status());
    }
}
