package com.flowpay.common.enums;

/**
 * Transaction status enumeration representing the transaction lifecycle.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED,
    CANCELLED
}
