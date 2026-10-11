package com.service.stagebet.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final StripeWebhookSignatureVerifier verifier = new StripeWebhookSignatureVerifier("test-secret", 300);
    private final java.util.Set<String> claimedEventIds = new java.util.HashSet<>();
    private final StripeWebhookService service = new StripeWebhookService(verifier,
            (eventId, receivedAt) -> claimedEventIds.add(eventId), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appliesAValidWebhookOnlyOnce() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"payment_intent.succeeded\"}";
        String signature = verifier.signatureForTests(payload, NOW);

        assertEquals("ACCEPTED", service.process(payload, signature).status());
        assertEquals("DUPLICATE", service.process(payload, signature).status());
    }

    @Test
    void rejectsAnInvalidWebhookSignature() {
        String payload = "{\"id\":\"evt_invalid\"}";

        assertThrows(IllegalArgumentException.class, () -> service.process(payload, "t=1767225600,v1=invalid"));
    }
}
