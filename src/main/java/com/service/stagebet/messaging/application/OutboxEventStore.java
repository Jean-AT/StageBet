package com.service.stagebet.messaging.application;

import java.util.UUID;

public interface OutboxEventStore {

    void append(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
            Long eventSequence, String payload, UUID correlationId, UUID causationId);

    static OutboxEventStore noop() {
        return (aggregateType, aggregateId, eventType, eventVersion, eventSequence, payload, correlationId, causationId) -> {
        };
    }
}
