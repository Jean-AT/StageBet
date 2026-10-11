package com.service.stagebet.game.application;

import com.service.stagebet.game.domain.RoundEvent;

public interface RoundEventPublisher {

    void publish(RoundEvent event, String serverSeedHash, String revealedServerSeed);
}
