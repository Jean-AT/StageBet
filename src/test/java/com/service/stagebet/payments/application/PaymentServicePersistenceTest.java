package com.service.stagebet.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentServicePersistenceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentStore paymentStore;

    @Test
    void storesProviderIntentAndRefundReferences() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String paymentKey = "payment-" + UUID.randomUUID();
        String refundKey = "refund-" + UUID.randomUUID();
        PaymentGateway.PaymentIntent paymentIntent = paymentService.createPaymentIntent(
                new PaymentGateway.PaymentCommand(tenantId, playerId, new BigDecimal("12.50"), "USD", paymentKey));

        PaymentGateway.RefundResult refund = paymentService.refund(new PaymentGateway.RefundCommand(tenantId, paymentIntent.paymentId(),
                new BigDecimal("12.50"), "USD", refundKey));

        PaymentStore.StoredPaymentIntent storedPaymentIntent = paymentStore.findPaymentIntent(tenantId, paymentIntent.paymentId()).orElseThrow();
        PaymentStore.StoredRefund storedRefund = paymentStore.findRefund(tenantId, paymentIntent.paymentId(), refundKey).orElseThrow();

        assertEquals(paymentIntent.providerReference(), storedPaymentIntent.providerReference());
        assertEquals(paymentIntent.status(), storedPaymentIntent.status());
        assertEquals(refund.providerReference(), storedRefund.providerReference());
        assertTrue(refund.providerReference().startsWith("fake_re_"));
    }
}
