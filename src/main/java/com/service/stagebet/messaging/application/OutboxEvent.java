package com.service.stagebet.messaging.application;

import java.util.UUID;

public record OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
        int eventVersion, Long eventSequence, String payload, UUID correlationId, UUID causationId,
        UUID claimToken) {

    public OutboxEvent withClaimToken(UUID nextClaimToken) {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, eventVersion, eventSequence, payload,
                correlationId, causationId, nextClaimToken);
    }
}
