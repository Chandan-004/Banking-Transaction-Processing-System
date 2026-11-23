package com.enterprise.banking;

import com.enterprise.banking.exception.*;
import com.enterprise.banking.model.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Exception Hierarchy & Category Mapping Tests")
class ExceptionHierarchyTest {

    @Test
    @DisplayName("Verify custom exceptions map to accurate ErrorCategory")
    void customExceptionsCategoryMapping() {
        InsufficientBalanceException ex1 = new InsufficientBalanceException("Low balance");
        assertEquals(ErrorCategory.INSUFFICIENT_FUNDS, ex1.getErrorCategory());
        assertTrue(ex1 instanceof BankingException);

        AccountNotFoundException ex2 = new AccountNotFoundException("Account missing");
        assertEquals(ErrorCategory.ACCOUNT_NOT_FOUND, ex2.getErrorCategory());
        assertTrue(ex2 instanceof BankingException);

        AccountInactiveException ex3 = new AccountInactiveException("Account suspended");
        assertEquals(ErrorCategory.ACCOUNT_INACTIVE, ex3.getErrorCategory());
        assertTrue(ex3 instanceof BankingException);

        DuplicateTransactionException ex4 = new DuplicateTransactionException("Duplicate TXN");
        assertEquals(ErrorCategory.DUPLICATE_TRANSACTION, ex4.getErrorCategory());
        assertTrue(ex4 instanceof BankingException);

        InvalidTransactionException ex5 = new InvalidTransactionException("Negative amount");
        assertEquals(ErrorCategory.INVALID_TRANSACTION, ex5.getErrorCategory());
        assertTrue(ex5 instanceof BankingException);
    }
}
