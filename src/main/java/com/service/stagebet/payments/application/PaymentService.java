package com.service.stagebet.payments.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentStore paymentStore;

    public PaymentService(PaymentGateway paymentGateway, PaymentStore paymentStore) {
        this.paymentGateway = paymentGateway;
        this.paymentStore = paymentStore;
    }

    @Transactional
    public PaymentGateway.PaymentIntent createPaymentIntent(PaymentGateway.PaymentCommand command) {
        return paymentStore.findPaymentIntent(command.tenantId(), command.idempotencyKey())
                .map(this::toPaymentIntent)
                .orElseGet(() -> savePaymentIntent(command, paymentGateway.createPaymentIntent(command)));
    }

    @Transactional
    public PaymentGateway.RefundResult refund(PaymentGateway.RefundCommand command) {
        return paymentStore.findRefund(command.tenantId(), command.paymentId(), command.idempotencyKey())
                .map(this::toRefundResult)
                .orElseGet(() -> refundStoredPayment(command));
    }

    private PaymentGateway.PaymentIntent savePaymentIntent(PaymentGateway.PaymentCommand command,
            PaymentGateway.PaymentIntent paymentIntent) {
        PaymentStore.StoredPaymentIntent stored = paymentStore.savePaymentIntent(new PaymentStore.StoredPaymentIntent(
                paymentIntent.paymentId(), command.tenantId(), command.playerId(), paymentIntent.provider(),
                paymentIntent.providerReference(), paymentIntent.amount(), paymentIntent.currency(), paymentIntent.status(),
                command.idempotencyKey()));
        return toPaymentIntent(stored);
    }

    private PaymentGateway.RefundResult refundStoredPayment(PaymentGateway.RefundCommand command) {
        PaymentStore.StoredPaymentIntent paymentIntent = paymentStore.findPaymentIntent(command.tenantId(), command.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("payment intent does not exist for this tenant"));
        PaymentGateway.RefundResult refund = paymentGateway.refund(new PaymentGateway.ProviderRefundCommand(command.tenantId(),
                command.paymentId(), paymentIntent.providerReference(), command.amount(), command.currency(), command.idempotencyKey()));
        PaymentStore.StoredRefund stored = paymentStore.saveRefund(new PaymentStore.StoredRefund(UUID.randomUUID(),
                command.tenantId(), command.paymentId(), refund.provider(), refund.providerReference(), refund.amount(),
                refund.currency(), refund.status(), command.idempotencyKey()));
        return toRefundResult(stored);
    }

    private PaymentGateway.PaymentIntent toPaymentIntent(PaymentStore.StoredPaymentIntent paymentIntent) {
        return new PaymentGateway.PaymentIntent(paymentIntent.provider(), paymentIntent.providerReference(), paymentIntent.paymentId(),
                paymentIntent.amount(), paymentIntent.currency(), paymentIntent.status());
    }

    private PaymentGateway.RefundResult toRefundResult(PaymentStore.StoredRefund refund) {
        return new PaymentGateway.RefundResult(refund.provider(), refund.providerReference(), refund.amount(),
                refund.currency(), refund.status());
    }
}
