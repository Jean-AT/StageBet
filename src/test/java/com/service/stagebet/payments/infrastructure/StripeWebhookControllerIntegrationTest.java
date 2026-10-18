package com.service.stagebet.payments.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.service.stagebet.payments.application.PaymentStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StripeWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StripeWebhookSignatureVerifier signatureVerifier;

    @Autowired
    private PaymentStore paymentStore;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void retriesAHandlerFailureAndAppliesTheSignedEventExactlyOnce() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt_" + UUID.randomUUID();
        String providerReference = "pi_" + UUID.randomUUID();
        String payload = "{\"id\":\"" + eventId + "\",\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"" + providerReference + "\",\"status\":\"succeeded\"}}}";
        String signature = signatureVerifier.signatureForTests(payload, Instant.now());

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is5xxServerError());
        assertEquals(0, eventCount(eventId));

        paymentStore.savePaymentIntent(new PaymentStore.StoredPaymentIntent(paymentId, tenantId, UUID.randomUUID(),
                "STRIPE_SANDBOX", providerReference, new BigDecimal("12.50"), "USD", "requires_capture", "payment-" + UUID.randomUUID()));

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertEquals(1, eventCount(eventId));
        assertEquals("succeeded", paymentStore.findPaymentIntent(tenantId, paymentId).orElseThrow().status());
    }

    private int eventCount(String eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = :eventId",
                new MapSqlParameterSource("eventId", eventId), Integer.class);
    }
}
