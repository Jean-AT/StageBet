package com.service.stagebet.payments.infrastructure;

import com.service.stagebet.identity.infrastructure.BettingOwnershipAuthorizer;
import com.service.stagebet.payments.application.PaymentGateway;
import com.service.stagebet.payments.application.PaymentService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "stagebet.payments.stripe", name = "enabled", havingValue = "true")
@RequestMapping("/api/tenants/{tenantId}/players/{playerId}/sandbox-checkout")
public class StripeSandboxCheckoutController {

    private final PaymentService paymentService;
    private final BettingOwnershipAuthorizer ownershipAuthorizer;
    private final String publishableKey;

    public StripeSandboxCheckoutController(PaymentService paymentService, BettingOwnershipAuthorizer ownershipAuthorizer,
            @Value("${stagebet.payments.stripe.publishable-key:}") String publishableKey) {
        this.paymentService = paymentService;
        this.ownershipAuthorizer = ownershipAuthorizer;
        if (publishableKey.isBlank()) throw new IllegalArgumentException("Stripe Sandbox requires a publishable key when enabled");
        this.publishableKey = publishableKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse create(@PathVariable UUID tenantId, @PathVariable UUID playerId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey, @RequestBody CreateCheckoutRequest request, Principal principal) {
        ownershipAuthorizer.authorize(principal, tenantId, playerId);
        PaymentGateway.CheckoutSession checkout = paymentService.prepareSandboxCheckout(new PaymentGateway.PaymentCommand(
                tenantId, playerId, request.amount(), request.currency(), idempotencyKey));
        return new CheckoutResponse(checkout.paymentId(), checkout.providerReference(), checkout.clientSecret(), publishableKey, checkout.status());
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentGateway.PaymentConfirmation confirm(@PathVariable UUID tenantId, @PathVariable UUID playerId,
            @PathVariable UUID paymentId, @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody ConfirmCheckoutRequest request, Principal principal) {
        ownershipAuthorizer.authorize(principal, tenantId, playerId);
        return paymentService.confirmSandboxCheckout(tenantId, playerId, paymentId, request.paymentMethodId(), idempotencyKey);
    }

    public record CreateCheckoutRequest(BigDecimal amount, String currency) { }
    public record ConfirmCheckoutRequest(String paymentMethodId) { }
    public record CheckoutResponse(UUID paymentId, String providerPaymentIntentId, String clientSecret,
            String publishableKey, String status) { }
}
