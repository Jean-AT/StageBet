package com.service.stagebet.betting.infrastructure;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.service.stagebet.betting.application.BetResult;
import com.service.stagebet.betting.application.BettingService;
import com.service.stagebet.betting.domain.BetStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BettingControllerTest {

    private final BettingService bettingService = mock(BettingService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BettingController(bettingService))
            .setControllerAdvice(new BettingExceptionHandler())
            .build();

    @Test
    void returnsTheStableDuplicateResultForRepeatedBetRequests() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID betId = UUID.randomUUID();
        when(bettingService.placeBet(any())).thenReturn(new BetResult(betId, roundId, playerId,
                new BigDecimal("10.00"), null, null, BetStatus.ACCEPTED, 1, true));

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets",
                        tenantId, playerId, roundId)
                .header("X-Idempotency-Key", "bet-http-duplicate")
                .contentType("application/json")
                .content("{\"betId\":\"" + betId + "\",\"amount\":10.00,\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.betId", is(betId.toString())))
                .andExpect(jsonPath("$.duplicate", is(true)));
    }
}
