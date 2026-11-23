package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Thrown when a specified account ID does not exist in the database.
 */
public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String message) {
        super(ErrorCategory.ACCOUNT_NOT_FOUND, message);
    }
}
