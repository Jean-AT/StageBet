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

    @Transactional
    public PaymentGateway.CheckoutSession prepareSandboxCheckout(PaymentGateway.PaymentCommand command) {
        PaymentGateway.PaymentIntent paymentIntent = createPaymentIntent(command);
        requireStripeSandbox(paymentIntent.provider());
        return paymentGateway.prepareCheckout(new PaymentGateway.ProviderCheckoutCommand(command.tenantId(),
                paymentIntent.paymentId(), paymentIntent.providerReference()));
    }

    @Transactional
    public PaymentGateway.PaymentConfirmation confirmSandboxCheckout(UUID tenantId, UUID playerId, UUID paymentId,
            String paymentMethodId, String idempotencyKey) {
        PaymentStore.StoredPaymentIntent paymentIntent = paymentStore.findPaymentIntent(tenantId, paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment intent does not exist for this tenant"));
        if (!paymentIntent.playerId().equals(playerId)) {
            throw new IllegalArgumentException("payment intent does not belong to this player");
        }
        requireStripeSandbox(paymentIntent.provider());
        if ("succeeded".equalsIgnoreCase(paymentIntent.status())) {
            return new PaymentGateway.PaymentConfirmation(paymentIntent.provider(), paymentIntent.providerReference(),
                    paymentIntent.paymentId(), paymentIntent.status(), null);
        }
        PaymentGateway.PaymentConfirmation confirmation = paymentGateway.confirmPaymentIntent(
                new PaymentGateway.ProviderConfirmationCommand(tenantId, paymentId, paymentIntent.providerReference(),
                        paymentMethodId, idempotencyKey));
        paymentStore.updatePaymentIntentStatus(confirmation.provider(), confirmation.providerReference(), confirmation.status());
        return confirmation;
    }

    private void requireStripeSandbox(String provider) {
        if (!"STRIPE_SANDBOX".equals(provider)) {
            throw new IllegalStateException("Stripe Sandbox checkout is not enabled for this payment");
        }
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
