package com.service.stagebet.wallet.application;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("wallet does not have enough available funds");
    }
}
