package com.service.stagebet.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    @Test
    void persistsProviderReferencesAndUsesTheStoredIntentForIdempotentRefunds() {
        RecordingPaymentGateway gateway = new RecordingPaymentGateway();
        PaymentService service = new PaymentService(gateway, new InMemoryPaymentStore());
        UUID tenantId = UUID.randomUUID();
        PaymentGateway.PaymentCommand paymentCommand = new PaymentGateway.PaymentCommand(tenantId, UUID.randomUUID(),
                new BigDecimal("12.50"), "USD", "payment-key");

        PaymentGateway.PaymentIntent created = service.createPaymentIntent(paymentCommand);
        PaymentGateway.PaymentIntent duplicate = service.createPaymentIntent(paymentCommand);
        PaymentGateway.RefundResult refunded = service.refund(new PaymentGateway.RefundCommand(tenantId, created.paymentId(),
                new BigDecimal("12.50"), "USD", "refund-key"));
        PaymentGateway.RefundResult duplicateRefund = service.refund(new PaymentGateway.RefundCommand(tenantId, created.paymentId(),
                new BigDecimal("12.50"), "USD", "refund-key"));

        assertEquals("pi_remote_123", created.providerReference());
        assertEquals(created, duplicate);
        assertEquals(1, gateway.paymentIntentCalls);
        assertEquals("pi_remote_123", gateway.refundPaymentIntentId);
        assertEquals("re_remote_456", refunded.providerReference());
        assertEquals(refunded, duplicateRefund);
        assertEquals(1, gateway.refundCalls);
    }

    private static final class RecordingPaymentGateway implements PaymentGateway {
        private int paymentIntentCalls;
        private int refundCalls;
        private String refundPaymentIntentId;

        @Override
        public PaymentIntent createPaymentIntent(PaymentCommand command) {
            paymentIntentCalls++;
            return new PaymentIntent("STRIPE_SANDBOX", "pi_remote_123", UUID.randomUUID(), command.amount(),
                    command.currency(), "succeeded");
        }

        @Override
        public RefundResult refund(ProviderRefundCommand command) {
            refundCalls++;
            refundPaymentIntentId = command.providerPaymentIntentId();
            return new RefundResult("STRIPE_SANDBOX", "re_remote_456", command.amount(), command.currency(), "succeeded");
        }
    }

    private static final class InMemoryPaymentStore implements PaymentStore {
        private final Map<String, StoredPaymentIntent> paymentIntentsByKey = new HashMap<>();
        private final Map<UUID, StoredPaymentIntent> paymentIntentsById = new HashMap<>();
        private final Map<String, StoredRefund> refunds = new HashMap<>();

        @Override
        public Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, String idempotencyKey) {
            return Optional.ofNullable(paymentIntentsByKey.get(paymentKey(tenantId, idempotencyKey)));
        }

        @Override
        public Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, UUID paymentId) {
            StoredPaymentIntent paymentIntent = paymentIntentsById.get(paymentId);
            return paymentIntent != null && paymentIntent.tenantId().equals(tenantId) ? Optional.of(paymentIntent) : Optional.empty();
        }

        @Override
        public StoredPaymentIntent savePaymentIntent(StoredPaymentIntent paymentIntent) {
            paymentIntentsByKey.put(paymentKey(paymentIntent.tenantId(), paymentIntent.idempotencyKey()), paymentIntent);
            paymentIntentsById.put(paymentIntent.paymentId(), paymentIntent);
            return paymentIntent;
        }

        @Override
        public Optional<StoredRefund> findRefund(UUID tenantId, UUID paymentId, String idempotencyKey) {
            return Optional.ofNullable(refunds.get(refundKey(tenantId, paymentId, idempotencyKey)));
        }

        @Override
        public StoredRefund saveRefund(StoredRefund refund) {
            refunds.put(refundKey(refund.tenantId(), refund.paymentId(), refund.idempotencyKey()), refund);
            return refund;
        }

        @Override
        public boolean updatePaymentIntentStatus(String provider, String providerReference, String status) {
            return paymentIntentsById.values().stream()
                    .filter(paymentIntent -> paymentIntent.provider().equals(provider)
                            && paymentIntent.providerReference().equals(providerReference))
                    .findFirst()
                    .map(paymentIntent -> true)
                    .orElse(false);
        }

        private String paymentKey(UUID tenantId, String idempotencyKey) {
            return tenantId + ":" + idempotencyKey;
        }

        private String refundKey(UUID tenantId, UUID paymentId, String idempotencyKey) {
            return tenantId + ":" + paymentId + ":" + idempotencyKey;
        }
    }
}
