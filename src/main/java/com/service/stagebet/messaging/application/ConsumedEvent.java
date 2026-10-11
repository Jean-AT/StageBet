package com.service.stagebet.messaging.application;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ConsumedEvent(UUID eventId, String eventType, int eventVersion, String aggregateType,
        UUID aggregateId, UUID correlationId, UUID causationId, JsonNode payload) {
}
