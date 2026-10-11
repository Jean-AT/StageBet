package com.service.stagebet.observability.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BettingMetrics {

    private final Counter acceptedBets;
    private final Counter duplicateBets;
    private final Counter cashOuts;
    private final Counter duplicateCashOuts;

    public BettingMetrics(MeterRegistry registry) {
        acceptedBets = registry.counter("stagebet_bets_total", "result", "accepted");
        duplicateBets = registry.counter("stagebet_bets_total", "result", "duplicate");
        cashOuts = registry.counter("stagebet_cashouts_total", "result", "completed");
        duplicateCashOuts = registry.counter("stagebet_cashouts_total", "result", "duplicate");
    }

    public void recordBet(boolean duplicate) {
        (duplicate ? duplicateBets : acceptedBets).increment();
    }

    public void recordCashOut(boolean duplicate) {
        (duplicate ? duplicateCashOuts : cashOuts).increment();
    }
}
