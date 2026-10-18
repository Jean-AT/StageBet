package com.service.stagebet.payments.infrastructure;

import com.service.stagebet.payments.application.StripeWebhookService;
import com.service.stagebet.payments.application.WebhookProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/webhooks")
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.OK)
    public StripeWebhookService.WebhookResult stripe(@RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {
        try {
            return webhookService.process(payload, signature);
        } catch (WebhookProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe webhook processing failed", exception);
        }
    }
}
