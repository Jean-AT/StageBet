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
        client.post().uri("/v1/payment_intents")
                .body(form)
                .retrieve().toBodilessEntity();
        return new PaymentIntent("STRIPE_SANDBOX", "stripe_pi_" + paymentId, paymentId,
                command.amount(), command.currency().toUpperCase(), "SUBMITTED");
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", command.paymentId().toString());
        form.add("amount", command.amount().movePointRight(2).toPlainString());
        client.post().uri("/v1/refunds")
                .body(form)
                .retrieve().toBodilessEntity();
        return new RefundResult("STRIPE_SANDBOX", "stripe_re_" + command.paymentId(), command.amount(),
                command.currency().toUpperCase(), "SUBMITTED");
    }
}
