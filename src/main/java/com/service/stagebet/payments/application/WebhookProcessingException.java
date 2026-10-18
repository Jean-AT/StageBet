package com.service.stagebet.payments.application;

public class WebhookProcessingException extends RuntimeException {

    public WebhookProcessingException(String message) {
        super(message);
    }
}
