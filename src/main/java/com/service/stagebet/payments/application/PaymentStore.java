package com.service.stagebet.payments.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStore {

    Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, String idempotencyKey);

    Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, UUID paymentId);

    StoredPaymentIntent savePaymentIntent(StoredPaymentIntent paymentIntent);

    Optional<StoredRefund> findRefund(UUID tenantId, UUID paymentId, String idempotencyKey);

    StoredRefund saveRefund(StoredRefund refund);

    boolean updatePaymentIntentStatus(String provider, String providerReference, String status);

    record StoredPaymentIntent(UUID paymentId, UUID tenantId, UUID playerId, String provider,
            String providerReference, BigDecimal amount, String currency, String status, String idempotencyKey) {
    }

    record StoredRefund(UUID refundId, UUID tenantId, UUID paymentId, String provider,
            String providerReference, BigDecimal amount, String currency, String status, String idempotencyKey) {
    }
}
