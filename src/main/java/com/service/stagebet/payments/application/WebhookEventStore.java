package com.service.stagebet.payments.application;

import java.time.Instant;

public interface WebhookEventStore {

    boolean claim(String eventId, String eventType, String paymentReference, Instant receivedAt);
}
