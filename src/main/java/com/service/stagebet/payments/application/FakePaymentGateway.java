package com.service.stagebet.payments.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public PaymentIntent createPaymentIntent(PaymentCommand command) {
        return new PaymentIntent("FAKE", "fake_pi_" + command.idempotencyKey(), UUID.randomUUID(),
                command.amount(), command.currency().toUpperCase(), "SUCCEEDED");
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        return new RefundResult("FAKE", "fake_re_" + command.idempotencyKey(), command.amount(),
                command.currency().toUpperCase(), "SUCCEEDED");
    }
}
