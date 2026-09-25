package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class InvalidSignatureException extends PayflowException {
    public InvalidSignatureException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
