package com.service.stagebet.game.application;

import com.service.stagebet.game.domain.CrashRound;
import java.util.Optional;
import java.util.UUID;

public interface CrashRoundStore {

    void save(CrashRound round);

    Optional<CrashRound> findById(UUID roundId);

    default Optional<CrashRound> findByIdForUpdate(UUID roundId) {
        return findById(roundId);
    }
}
