package com.service.stagebet.identity.domain;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("command rate limit exceeded");
    }
}
