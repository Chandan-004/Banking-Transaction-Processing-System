package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Thrown when an incoming transaction ID has already been processed or is currently in flight.
 */
public class DuplicateTransactionException extends BankingException {

    public DuplicateTransactionException(String message) {
        super(ErrorCategory.DUPLICATE_TRANSACTION, message);
    }
}
