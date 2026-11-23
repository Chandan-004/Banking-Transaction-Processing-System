package com.enterprise.banking.model;

/**
 * Standard error categories for failure auditing and reporting.
 */
public enum ErrorCategory {
    INSUFFICIENT_FUNDS,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_INACTIVE,
    DUPLICATE_TRANSACTION,
    INVALID_AMOUNT,
    INVALID_TRANSACTION,
    LOCK_TIMEOUT,
    SYSTEM_ERROR
}
