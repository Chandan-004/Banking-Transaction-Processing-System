package com.enterprise.banking.exception;

import com.enterprise.banking.model.ErrorCategory;

/**
 * Thrown when an account is in SUSPENDED or CLOSED status.
 */
public class AccountInactiveException extends BankingException {

    public AccountInactiveException(String message) {
        super(ErrorCategory.ACCOUNT_INACTIVE, message);
    }
}
