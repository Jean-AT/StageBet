package com.service.stagebet.payments.application;

import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StripeWebhookService {

    private final StripeWebhookSignatureVerifier signatureVerifier;
    private final WebhookEventStore eventStore;
    private final PaymentStore paymentStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public StripeWebhookService(StripeWebhookSignatureVerifier signatureVerifier, WebhookEventStore eventStore,
            PaymentStore paymentStore, ObjectMapper objectMapper) {
        this(signatureVerifier, eventStore, paymentStore, objectMapper, Clock.systemUTC());
    }

    StripeWebhookService(StripeWebhookSignatureVerifier signatureVerifier, WebhookEventStore eventStore,
            PaymentStore paymentStore, ObjectMapper objectMapper, Clock clock) {
        this.signatureVerifier = signatureVerifier;
        this.eventStore = eventStore;
        this.paymentStore = paymentStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public WebhookResult process(String payload, String signatureHeader) {
        signatureVerifier.verify(payload, signatureHeader, clock.instant());
        StripeEvent event = parseEvent(payload);
        if (!eventStore.claim(event.id(), event.type(), event.paymentReference(), clock.instant())) {
            return new WebhookResult(event.id(), false, "DUPLICATE");
        }
        if (!isHandled(event.type())) {
            return new WebhookResult(event.id(), true, "IGNORED");
        }
        if (!paymentStore.updatePaymentIntentStatus("STRIPE_SANDBOX", event.paymentReference(), event.paymentStatus())) {
            throw new WebhookProcessingException("Stripe webhook references an unknown payment intent");
        }
        return new WebhookResult(event.id(), true, "APPLIED");
    }

    private StripeEvent parseEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = requiredText(event, "id", "Stripe webhook is missing an event id");
            String eventType = requiredText(event, "type", "Stripe webhook is missing an event type");
            JsonNode paymentIntent = event.path("data").path("object");
            if (!isHandled(eventType)) {
                return new StripeEvent(eventId, eventType, null, null);
            }
            return new StripeEvent(eventId, eventType,
                    requiredText(paymentIntent, "id", "Stripe webhook is missing a PaymentIntent id"),
                    requiredText(paymentIntent, "status", "Stripe webhook is missing a PaymentIntent status"));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Stripe webhook contains invalid JSON", exception);
        }
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private boolean isHandled(String eventType) {
        return "payment_intent.succeeded".equals(eventType)
                || "payment_intent.payment_failed".equals(eventType)
                || "payment_intent.canceled".equals(eventType);
    }

    public record WebhookResult(String eventId, boolean applied, String status) {
    }

    private record StripeEvent(String id, String type, String paymentReference, String paymentStatus) {
    }
}
