package com.payflow.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends PayflowException {
    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
