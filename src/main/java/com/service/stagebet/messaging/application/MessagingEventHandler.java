package com.service.stagebet.messaging.application;

public interface MessagingEventHandler {

    boolean supports(String eventType);

    void handle(ConsumedEvent event);
}
