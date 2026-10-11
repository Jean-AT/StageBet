package com.service.stagebet.game.infrastructure;

import com.service.stagebet.game.application.RoundEventPublisher;
import com.service.stagebet.game.domain.RoundEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketRoundEventPublisher implements RoundEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketRoundEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(RoundEvent event, String serverSeedHash, String revealedServerSeed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundId", event.roundId());
        payload.put("sequence", event.sequence());
        payload.put("status", event.status());
        payload.put("multiplier", event.multiplier());
        payload.put("serverSeedHash", serverSeedHash);
        payload.put("occurredAt", event.occurredAt());
        if (revealedServerSeed != null) {
            payload.put("serverSeed", revealedServerSeed);
        }
        messagingTemplate.convertAndSend("/topic/rounds/" + event.roundId(), payload, Map.of());
    }
}
