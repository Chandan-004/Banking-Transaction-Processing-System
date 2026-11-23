package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Thrown when validation rules fail (e.g. non-positive amount, self-transfer).
 */
public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(ErrorCategory.INVALID_TRANSACTION, message);
    }

    public InvalidTransactionException(ErrorCategory category, String message) {
        super(category, message);
    }
}
