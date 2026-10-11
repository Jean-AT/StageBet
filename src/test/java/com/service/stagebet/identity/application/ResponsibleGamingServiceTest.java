package com.service.stagebet.identity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.service.stagebet.identity.domain.ResponsibleGamingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ResponsibleGamingServiceTest {

    @Autowired
    private ResponsibleGamingService responsibleGamingService;

    @Test
    void rejectsBetsAfterTheDailyLimitIsReached() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        responsibleGamingService.updateRestrictions(tenantId, playerId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), null, null, null, false);

        assertThrows(ResponsibleGamingException.class,
                () -> responsibleGamingService.validateAndRecordBet(tenantId, playerId, new BigDecimal("11.00")));
    }

    @Test
    void rejectsSelfExcludedPlayersBeforeAReservationCanBeMade() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        responsibleGamingService.updateRestrictions(tenantId, playerId, UUID.randomUUID(), UUID.randomUUID(),
                null, null, Instant.now().plusSeconds(3600), null, false);

        assertThrows(ResponsibleGamingException.class,
                () -> responsibleGamingService.validateAndRecordBet(tenantId, playerId, new BigDecimal("1.00")));
    }
}
