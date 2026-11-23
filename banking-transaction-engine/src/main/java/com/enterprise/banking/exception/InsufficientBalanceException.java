package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Thrown when an account does not have sufficient funds for a debit operation.
 */
public class InsufficientBalanceException extends BankingException {

    public InsufficientBalanceException(String message) {
        super(ErrorCategory.INSUFFICIENT_FUNDS, message);
    }
}
