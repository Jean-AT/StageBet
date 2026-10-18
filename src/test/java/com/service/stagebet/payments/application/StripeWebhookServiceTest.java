package com.service.stagebet.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class StripeWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final StripeWebhookSignatureVerifier verifier = new StripeWebhookSignatureVerifier("test-secret", 300);
    private final java.util.Set<String> claimedEventIds = new java.util.HashSet<>();
    private final PaymentStore paymentStore = Mockito.mock(PaymentStore.class);
    private final StripeWebhookService service = new StripeWebhookService(verifier,
            (eventId, eventType, paymentReference, receivedAt) -> claimedEventIds.add(eventId), paymentStore,
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appliesAValidWebhookOnlyOnce() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"pi_123\",\"status\":\"succeeded\"}}}";
        String signature = verifier.signatureForTests(payload, NOW);
        when(paymentStore.updatePaymentIntentStatus("STRIPE_SANDBOX", "pi_123", "succeeded")).thenReturn(true);

        assertEquals("APPLIED", service.process(payload, signature).status());
        assertEquals("DUPLICATE", service.process(payload, signature).status());
        verify(paymentStore).updatePaymentIntentStatus("STRIPE_SANDBOX", "pi_123", "succeeded");
    }

    @Test
    void rejectsAnInvalidWebhookSignature() {
        String payload = "{\"id\":\"evt_invalid\"}";

        assertThrows(IllegalArgumentException.class, () -> service.process(payload, "t=1767225600,v1=invalid"));
    }

    @Test
    void leavesUnknownEventTypesRecordedWithoutChangingPaymentState() {
        String payload = "{\"id\":\"evt_ignored\",\"type\":\"customer.created\"}";
        String signature = verifier.signatureForTests(payload, NOW);

        assertEquals("IGNORED", service.process(payload, signature).status());
    }
}
