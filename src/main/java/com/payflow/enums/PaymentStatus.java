package com.payflow.enums;

public enum PaymentStatus {
    INITIATED,
    PENDING_GATEWAY,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED,
    RETRYING
}
