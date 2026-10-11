package com.service.stagebet.payments.application;

import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookService {

    private final StripeWebhookSignatureVerifier signatureVerifier;
    private final WebhookEventStore eventStore;
    private final Clock clock;

    @Autowired
    public StripeWebhookService(StripeWebhookSignatureVerifier signatureVerifier, WebhookEventStore eventStore) {
        this(signatureVerifier, eventStore, Clock.systemUTC());
    }

    StripeWebhookService(StripeWebhookSignatureVerifier signatureVerifier, WebhookEventStore eventStore, Clock clock) {
        this.signatureVerifier = signatureVerifier;
        this.eventStore = eventStore;
        this.clock = clock;
    }

    public WebhookResult process(String payload, String signatureHeader) {
        signatureVerifier.verify(payload, signatureHeader, clock.instant());
        String eventId = eventId(payload);
        if (!eventStore.claim(eventId, clock.instant())) {
            return new WebhookResult(eventId, false, "DUPLICATE");
        }
        return new WebhookResult(eventId, true, "ACCEPTED");
    }

    private String eventId(String payload) {
        int marker = payload.indexOf("\"id\"");
        if (marker < 0) {
            throw new IllegalArgumentException("Stripe webhook is missing an event id");
        }
        int start = payload.indexOf('"', payload.indexOf(':', marker) + 1) + 1;
        int end = payload.indexOf('"', start);
        if (start <= 0 || end < start) {
            throw new IllegalArgumentException("Stripe webhook contains an invalid event id");
        }
        return payload.substring(start, end);
    }

    public record WebhookResult(String eventId, boolean applied, String status) {
    }
}
