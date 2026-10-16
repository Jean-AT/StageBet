package com.service.stagebet.game.application;

import java.util.UUID;

public interface RoundBetSettlement {

    int settleCrashedRound(UUID roundId);

    int refundCancelledRound(UUID roundId);

    static RoundBetSettlement noop() {
        return new RoundBetSettlement() {
            @Override
            public int settleCrashedRound(UUID roundId) {
                return 0;
            }

            @Override
            public int refundCancelledRound(UUID roundId) {
                return 0;
            }
        };
    }
}
