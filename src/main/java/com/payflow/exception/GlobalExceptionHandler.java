package com.payflow.exception;

import com.payflow.common.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private Map<String, Object> buildErrorBody(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("correlationId", MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        body.put("timestamp", LocalDateTime.now());
        return body;
    }

    @ExceptionHandler(PayflowException.class)
    public ResponseEntity<Map<String, Object>> handlePayflowException(PayflowException ex) {
        log.warn("Payflow business exception: status={}, message={}", ex.getStatus(), ex.getMessage());
        Map<String, Object> body = buildErrorBody(ex.getStatus(), ex.getStatus().getReasonPhrase(), ex.getMessage());
        return new ResponseEntity<>(body, ex.getStatus());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        Map<String, Object> body = buildErrorBody(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        Map<String, Object> body = buildErrorBody(HttpStatus.FORBIDDEN, "Forbidden", "Access denied: insufficient permissions");
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        HttpStatus httpStatus = HttpStatus.valueOf(status.value());
        Map<String, Object> body = buildErrorBody(httpStatus, "Validation Failed", "One or more request fields are invalid");
        body.put("fieldErrors", fieldErrors);

        log.warn("Request validation failed: {}", fieldErrors);
        return new ResponseEntity<>(body, httpStatus);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        Map<String, Object> body = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred while processing the request"
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
