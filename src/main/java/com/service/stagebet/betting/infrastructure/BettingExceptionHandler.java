package com.service.stagebet.betting.infrastructure;

import com.service.stagebet.identity.domain.ResponsibleGamingException;
import com.service.stagebet.wallet.application.InsufficientFundsException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BettingExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> insufficientFunds(InsufficientFundsException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(ResponsibleGamingException.class)
    public ResponseEntity<ErrorResponse> responsibleGaming(ResponsibleGamingException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> invalidState(IllegalStateException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, Instant.now()));
    }

    public record ErrorResponse(int status, String message, Instant timestamp) {
    }
}
