package com.service.stagebet.payments.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.service.stagebet.payments.application.PaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class StripeSandboxPaymentGatewayContractTest {

    private static WireMockServer stripe;
    private StripeSandboxPaymentGateway gateway;

    @BeforeAll
    static void startStripe() {
        stripe = new WireMockServer(0);
        stripe.start();
    }

    @AfterAll
    static void stopStripe() {
        stripe.stop();
    }

    @BeforeEach
    void setUp() {
        stripe.resetAll();
        gateway = new StripeSandboxPaymentGateway(RestClient.builder(), stripe.baseUrl(), "sk_test_contract");
    }

    @Test
    void createsAnIntentFromStripeResponseAndSendsAnIdempotencyKey() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .withHeader("Authorization", equalTo("Bearer sk_test_contract"))
                .withHeader("Idempotency-Key", equalTo("payment-key"))
                .withRequestBody(containing("amount=1250"))
                .withRequestBody(containing("currency=usd"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_remote_123\",\"status\":\"requires_capture\"}")));

        PaymentGateway.PaymentIntent paymentIntent = gateway.createPaymentIntent(paymentCommand("payment-key"));

        assertEquals("pi_remote_123", paymentIntent.providerReference());
        assertEquals("requires_capture", paymentIntent.status());
    }

    @Test
    void refundsTheRemoteIntentAndReturnsTheRemoteRefundReference() {
        stripe.stubFor(post(urlEqualTo("/v1/refunds"))
                .withHeader("Idempotency-Key", equalTo("refund-key"))
                .withRequestBody(containing("payment_intent=pi_remote_123"))
                .withRequestBody(containing("amount=1250"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"re_remote_456\",\"status\":\"succeeded\"}")));

        PaymentGateway.RefundResult refund = gateway.refund(new PaymentGateway.ProviderRefundCommand(UUID.randomUUID(), UUID.randomUUID(),
                "pi_remote_123", new BigDecimal("12.50"), "USD", "refund-key"));

        assertEquals("re_remote_456", refund.providerReference());
        assertEquals("succeeded", refund.status());
    }

    @Test
    void surfacesProviderFailuresAndAllowsTheCallerToRetryWithTheSameKey() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .inScenario("payment intent retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("recovered")
                .willReturn(aResponse().withStatus(503)));
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .inScenario("payment intent retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_retried_789\",\"status\":\"succeeded\"}")));

        assertThrows(RestClientResponseException.class, () -> gateway.createPaymentIntent(paymentCommand("retry-key")));
        PaymentGateway.PaymentIntent retried = gateway.createPaymentIntent(paymentCommand("retry-key"));

        assertEquals("pi_retried_789", retried.providerReference());
        stripe.verify(2, postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Idempotency-Key", equalTo("retry-key")));
    }

    private PaymentGateway.PaymentCommand paymentCommand(String idempotencyKey) {
        return new PaymentGateway.PaymentCommand(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.50"), "USD", idempotencyKey);
    }
}
