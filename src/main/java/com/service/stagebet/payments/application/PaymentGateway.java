package com.service.stagebet.payments.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    PaymentIntent createPaymentIntent(PaymentCommand command);

    RefundResult refund(ProviderRefundCommand command);

    record PaymentCommand(UUID tenantId, UUID playerId, BigDecimal amount, String currency, String idempotencyKey) {
    }

    record RefundCommand(UUID tenantId, UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
    }

    record ProviderRefundCommand(UUID tenantId, UUID paymentId, String providerPaymentIntentId, BigDecimal amount,
            String currency, String idempotencyKey) {
    }

    record PaymentIntent(String provider, String providerReference, UUID paymentId, BigDecimal amount,
            String currency, String status) {
    }

    record RefundResult(String provider, String providerReference, BigDecimal amount, String currency, String status) {
    }
}
