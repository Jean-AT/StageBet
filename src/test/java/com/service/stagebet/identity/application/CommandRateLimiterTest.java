package com.service.stagebet.identity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.service.stagebet.identity.domain.RateLimitExceededException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class CommandRateLimiterTest {

    @Test
    void rejectsCommandsAfterTheRedisWindowLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("1"))).thenReturn(3L);
        CommandRateLimiter limiter = new CommandRateLimiter(redis, 2, Duration.ofSeconds(1));

        assertThrows(RateLimitExceededException.class,
                () -> limiter.check(UUID.randomUUID(), UUID.randomUUID()));
    }
}
