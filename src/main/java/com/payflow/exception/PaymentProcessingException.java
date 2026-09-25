package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class PaymentProcessingException extends PayflowException {
    public PaymentProcessingException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public PaymentProcessingException(String message, HttpStatus status) {
        super(message, status);
    }
}
