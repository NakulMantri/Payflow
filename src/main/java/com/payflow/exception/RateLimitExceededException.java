package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends PayflowException {
    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
