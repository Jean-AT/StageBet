package com.service.stagebet.payments.application;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stagebet.payments.stripe", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public PaymentIntent createPaymentIntent(PaymentCommand command) {
        return new PaymentIntent("FAKE", "fake_pi_" + command.idempotencyKey(), UUID.randomUUID(),
                command.amount(), command.currency().toUpperCase(), "SUCCEEDED");
    }

    @Override
    public CheckoutSession prepareCheckout(ProviderCheckoutCommand command) {
        throw new IllegalStateException("Stripe Sandbox checkout is disabled");
    }

    @Override
    public PaymentConfirmation confirmPaymentIntent(ProviderConfirmationCommand command) {
        throw new IllegalStateException("Stripe Sandbox checkout is disabled");
    }

    @Override
    public RefundResult refund(ProviderRefundCommand command) {
        return new RefundResult("FAKE", "fake_re_" + command.idempotencyKey(), command.amount(),
                command.currency().toUpperCase(), "SUCCEEDED");
    }
}
