package com.service.stagebet.betting.infrastructure;

import com.service.stagebet.betting.application.BetResult;
import com.service.stagebet.betting.application.BettingService;
import com.service.stagebet.betting.application.CashOutCommand;
import com.service.stagebet.betting.application.PlaceBetCommand;
import com.service.stagebet.identity.application.CommandRateLimiter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/players/{playerId}")
public class BettingController {

    private final BettingService bettingService;
    private final CommandRateLimiter rateLimiter;

    public BettingController(BettingService bettingService) {
        this(bettingService, null);
    }

    @Autowired
    public BettingController(BettingService bettingService, CommandRateLimiter rateLimiter) {
        this.bettingService = bettingService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/rounds/{roundId}/bets")
    @ResponseStatus(HttpStatus.OK)
    public BetResult placeBet(@PathVariable UUID tenantId, @PathVariable UUID playerId,
            @PathVariable UUID roundId, @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody PlaceBetRequest request) {
        checkRateLimit(tenantId, playerId);
        return bettingService.placeBet(new PlaceBetCommand(tenantId, playerId, roundId, request.betId(),
                request.amount(), request.currency(), request.expectedRoundSequence(), idempotencyKey, null));
    }

    @PostMapping("/bets/{betId}/cash-out")
    @ResponseStatus(HttpStatus.OK)
    public BetResult cashOut(@PathVariable UUID tenantId, @PathVariable UUID playerId,
            @PathVariable UUID betId, @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CashOutRequest request) {
        checkRateLimit(tenantId, playerId);
        return bettingService.cashOut(new CashOutCommand(tenantId, playerId, betId,
                request.expectedRoundSequence(), idempotencyKey, null));
    }

    private void checkRateLimit(UUID tenantId, UUID playerId) {
        if (rateLimiter != null) {
            rateLimiter.check(tenantId, playerId);
        }
    }

    public record PlaceBetRequest(UUID betId, java.math.BigDecimal amount, String currency,
            long expectedRoundSequence) {
    }

    public record CashOutRequest(long expectedRoundSequence) {
    }
}
