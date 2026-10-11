package com.service.stagebet.identity.application;

import com.service.stagebet.identity.domain.RateLimitExceededException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class CommandRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long maximumRequests;
    private final Duration window;

    public CommandRateLimiter(StringRedisTemplate redisTemplate,
            @Value("${stagebet.rate-limit.max-requests:20}") long maximumRequests,
            @Value("${stagebet.rate-limit.window:PT1S}") Duration window) {
        if (maximumRequests < 1 || window.getSeconds() < 1) {
            throw new IllegalArgumentException("rate-limit settings must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.maximumRequests = maximumRequests;
        this.window = window;
    }

    public void check(UUID tenantId, UUID playerId) {
        String key = "stagebet:rate-limit:betting:" + tenantId + ":" + playerId;
        Long requestCount = redisTemplate.execute(INCREMENT_WINDOW, List.of(key),
                Long.toString(window.toSeconds()));
        if (requestCount != null && requestCount > maximumRequests) {
            throw new RateLimitExceededException();
        }
    }
}
