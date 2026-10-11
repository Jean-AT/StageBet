package com.service.stagebet.messaging.infrastructure;

import com.service.stagebet.messaging.application.ConsumedEvent;
import com.service.stagebet.messaging.application.InboxService;
import com.service.stagebet.messaging.application.MessagingEventHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "stagebet.messaging", name = "consumer-enabled", havingValue = "true")
public class KafkaEventConsumer {

    private final ObjectMapper objectMapper;
    private final InboxService inbox;
    private final List<MessagingEventHandler> handlers;
    private final String consumerName;

    public KafkaEventConsumer(ObjectMapper objectMapper, InboxService inbox, List<MessagingEventHandler> handlers,
            @Value("${stagebet.messaging.consumer-name:stagebet-default-consumer}") String consumerName) {
        this.objectMapper = objectMapper;
        this.inbox = inbox;
        this.handlers = handlers;
        this.consumerName = consumerName;
    }

    @KafkaListener(topics = "${stagebet.messaging.topic:wager.events.v1}",
            groupId = "${stagebet.messaging.consumer-group:stagebet-default}")
    public void consume(String message) {
        ConsumedEvent event = parse(message);
        List<MessagingEventHandler> matchingHandlers = handlers.stream()
                .filter(handler -> handler.supports(event.eventType()))
                .toList();
        if (matchingHandlers.isEmpty()) {
            throw new IllegalStateException("no handler registered for event type: " + event.eventType());
        }
        inbox.process(consumerName, event, ignored -> matchingHandlers.forEach(handler -> handler.handle(event)));
    }

    private ConsumedEvent parse(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            return new ConsumedEvent(requiredUuid(envelope, "eventId"), requiredText(envelope, "eventType"),
                    requiredInt(envelope, "eventVersion"), requiredText(envelope, "aggregateType"),
                    requiredUuid(envelope, "aggregateId"), requiredUuid(envelope, "correlationId"),
                    nullableUuid(envelope, "causationId"), requiredNode(envelope, "payload"));
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid messaging event envelope", exception);
        }
    }

    private String requiredText(JsonNode envelope, String field) {
        JsonNode value = requiredNode(envelope, field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("event field must be non-blank text: " + field);
        }
        return value.textValue();
    }

    private int requiredInt(JsonNode envelope, String field) {
        JsonNode value = requiredNode(envelope, field);
        if (!value.canConvertToInt() || value.intValue() < 1) {
            throw new IllegalArgumentException("event field must be a positive integer: " + field);
        }
        return value.intValue();
    }

    private UUID requiredUuid(JsonNode envelope, String field) {
        return UUID.fromString(requiredText(envelope, field));
    }

    private UUID nullableUuid(JsonNode envelope, String field) {
        JsonNode value = envelope.get(field);
        return value == null || value.isNull() ? null : UUID.fromString(requiredText(envelope, field));
    }

    private JsonNode requiredNode(JsonNode envelope, String field) {
        JsonNode value = envelope.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing event field: " + field);
        }
        return value;
    }
}
