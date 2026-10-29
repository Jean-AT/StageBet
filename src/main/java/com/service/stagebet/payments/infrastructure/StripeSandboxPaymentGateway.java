package com.service.stagebet.payments.infrastructure;

import com.service.stagebet.payments.application.PaymentGateway;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "stagebet.payments.stripe", name = "enabled", havingValue = "true")
public class StripeSandboxPaymentGateway implements PaymentGateway {

    private final RestClient client;

    public StripeSandboxPaymentGateway(RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value("${stagebet.payments.stripe.base-url:https://api.stripe.com}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${stagebet.payments.stripe.secret-key:}") String secretKey) {
        if (secretKey.isBlank()) {
            throw new IllegalArgumentException("Stripe Sandbox requires an explicit secret key when enabled");
        }
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    @Override
    public PaymentIntent createPaymentIntent(PaymentCommand command) {
        UUID paymentId = UUID.randomUUID();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", command.amount().movePointRight(2).toPlainString());
        form.add("currency", command.currency().toLowerCase());
        form.add("metadata[tenant_id]", command.tenantId().toString());
        form.add("metadata[player_id]", command.playerId().toString());
        form.add("metadata[payment_id]", paymentId.toString());
        StripePaymentIntent response = client.post().uri("/v1/payment_intents")
                .header("Idempotency-Key", command.idempotencyKey())
                .body(form)
                .retrieve().body(StripePaymentIntent.class);
        if (response == null || response.id() == null || response.id().isBlank()
                || response.status() == null || response.status().isBlank()) {
            throw new IllegalStateException("Stripe returned an incomplete PaymentIntent response");
        }
        return new PaymentIntent("STRIPE_SANDBOX", response.id(), paymentId,
                command.amount(), command.currency().toUpperCase(), response.status());
    }

    @Override
    public RefundResult refund(ProviderRefundCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", command.providerPaymentIntentId());
        form.add("amount", command.amount().movePointRight(2).toPlainString());
        StripeRefund response = client.post().uri("/v1/refunds")
                .header("Idempotency-Key", command.idempotencyKey())
                .body(form)
                .retrieve().body(StripeRefund.class);
        if (response == null || response.id() == null || response.id().isBlank()
                || response.status() == null || response.status().isBlank()) {
            throw new IllegalStateException("Stripe returned an incomplete refund response");
        }
        return new RefundResult("STRIPE_SANDBOX", response.id(), command.amount(),
                command.currency().toUpperCase(), response.status());
    }

    @Override
    public CheckoutSession prepareCheckout(ProviderCheckoutCommand command) {
        StripePaymentIntent response = client.get().uri("/v1/payment_intents/{id}", command.providerPaymentIntentId())
                .retrieve().body(StripePaymentIntent.class);
        return checkoutSession(command, response);
    }

    @Override
    public PaymentConfirmation confirmPaymentIntent(ProviderConfirmationCommand command) {
        if (command.paymentMethodId() == null || !command.paymentMethodId().startsWith("pm_")) {
            throw new IllegalArgumentException("Stripe checkout requires a PaymentMethod id");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_method", command.paymentMethodId());
        StripePaymentIntent response = client.post().uri("/v1/payment_intents/{id}/confirm", command.providerPaymentIntentId())
                .header("Idempotency-Key", command.idempotencyKey()).body(form).retrieve().body(StripePaymentIntent.class);
        if (response == null || response.id() == null || response.status() == null || response.status().isBlank()) {
            throw new IllegalStateException("Stripe returned an incomplete PaymentIntent confirmation");
        }
        return new PaymentConfirmation("STRIPE_SANDBOX", response.id(), command.paymentId(), response.status(), response.clientSecret());
    }

    private CheckoutSession checkoutSession(ProviderCheckoutCommand command, StripePaymentIntent response) {
        if (response == null || response.id() == null || response.clientSecret() == null || response.clientSecret().isBlank()
                || response.status() == null || response.status().isBlank()) {
            throw new IllegalStateException("Stripe returned an incomplete PaymentIntent checkout response");
        }
        return new CheckoutSession("STRIPE_SANDBOX", response.id(), command.paymentId(), response.clientSecret(), response.status());
    }

    private record StripePaymentIntent(String id, String status, @com.fasterxml.jackson.annotation.JsonProperty("client_secret") String clientSecret) {
    }

    private record StripeRefund(String id, String status) {
    }
}
