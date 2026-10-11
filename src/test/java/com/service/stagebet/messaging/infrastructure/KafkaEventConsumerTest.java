package com.service.stagebet.messaging.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.service.stagebet.messaging.application.ConsumedEvent;
import com.service.stagebet.messaging.application.InboxService;
import com.service.stagebet.messaging.application.MessagingEventHandler;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class KafkaEventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void dispatchesSupportedEventsThroughTheInboxBoundary() {
        InboxService inbox = org.mockito.Mockito.mock(InboxService.class);
        MessagingEventHandler handler = org.mockito.Mockito.mock(MessagingEventHandler.class);
        when(handler.supports("bet.accepted.v1")).thenReturn(true);
        when(inbox.process(eq("test-consumer"), any(ConsumedEvent.class), any(Consumer.class))).thenAnswer(invocation -> {
            ((Consumer<ConsumedEvent>) invocation.getArgument(2)).accept(invocation.getArgument(1));
            return true;
        });
        KafkaEventConsumer consumer = new KafkaEventConsumer(new ObjectMapper(), inbox, List.of(handler), "test-consumer");

        consumer.consume(event("bet.accepted.v1"));

        verify(handler).handle(any(ConsumedEvent.class));
    }

    @Test
    void rejectsEventsWithoutARegisteredHandlerSoKafkaCanRetryThem() {
        KafkaEventConsumer consumer = new KafkaEventConsumer(new ObjectMapper(), org.mockito.Mockito.mock(InboxService.class),
                List.of(), "test-consumer");

        assertThrows(IllegalStateException.class, () -> consumer.consume(event("unknown.v1")));
    }

    @Test
    void rejectsMalformedEnvelopesBeforeClaimingTheInbox() {
        InboxService inbox = org.mockito.Mockito.mock(InboxService.class);
        KafkaEventConsumer consumer = new KafkaEventConsumer(new ObjectMapper(), inbox, List.of(), "test-consumer");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume("{\"eventId\":\"not-a-uuid\"}"));

        org.mockito.Mockito.verifyNoInteractions(inbox);
    }

    private String event(String eventType) {
        return "{\"eventId\":\"" + EVENT_ID + "\",\"eventType\":\"" + eventType
                + "\",\"eventVersion\":1,\"aggregateType\":\"BET\",\"aggregateId\":\""
                + AGGREGATE_ID + "\",\"correlationId\":\"" + CORRELATION_ID
                + "\",\"causationId\":null,\"payload\":{\"status\":\"ACCEPTED\"}}";
    }
}
