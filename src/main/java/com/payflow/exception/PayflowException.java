package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class PayflowException extends RuntimeException {

    private final HttpStatus status;

    public PayflowException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public PayflowException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public PayflowException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
