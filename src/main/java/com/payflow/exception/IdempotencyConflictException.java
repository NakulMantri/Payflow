package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends PayflowException {
    public IdempotencyConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
