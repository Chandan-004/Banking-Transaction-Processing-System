package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Base unchecked exception for all banking engine domain errors.
 */
public class BankingException extends RuntimeException {

    private final ErrorCategory errorCategory;

    public BankingException(ErrorCategory errorCategory, String message) {
        super(message);
        this.errorCategory = errorCategory;
    }

    public BankingException(ErrorCategory errorCategory, String message, Throwable cause) {
        super(message, cause);
        this.errorCategory = errorCategory;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }
}
