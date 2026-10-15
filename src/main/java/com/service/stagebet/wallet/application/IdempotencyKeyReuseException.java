package com.service.stagebet.wallet.application;

public class IdempotencyKeyReuseException extends IllegalStateException {

    public IdempotencyKeyReuseException() {
        super("idempotency key was already used with different command data");
    }
}
