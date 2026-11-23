package com.enterprise.banking;

import com.enterprise.banking.model.Account;
import com.enterprise.banking.model.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account Entity Unit Tests")
class AccountTest {

    @Test
    @DisplayName("Default constructor initializes with zero balance and ACTIVE status")
    void defaultConstructorInitializesSafely() {
        Account account = new Account();
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals("INR", account.getCurrency());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(account.isActive());
    }

    @Test
    @DisplayName("Credit operation increases balance accurately")
    void creditIncreasesBalance() {
        Account account = new Account();
        account.credit(new BigDecimal("1000.50"));
        assertEquals(new BigDecimal("1000.50"), account.getBalance());

        account.credit(new BigDecimal("500.25"));
        assertEquals(new BigDecimal("1500.75"), account.getBalance());
    }

    @Test
    @DisplayName("Debit operation decreases balance accurately when funds are sufficient")
    void debitDecreasesBalanceWhenSufficient() {
        Account account = new Account();
        account.credit(new BigDecimal("10000.00"));

        account.debit(new BigDecimal("7000.00"));
        assertEquals(new BigDecimal("3000.00"), account.getBalance());

        account.debit(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("0.00"), account.getBalance());
    }

    @Test
    @DisplayName("Debit operation rejects overdraft and prevents negative balance")
    void debitThrowsWhenInsufficient() {
        Account account = new Account();
        account.credit(new BigDecimal("1000.00"));

        // Attempting to debit 1000.01 must fail
        assertThrows(IllegalStateException.class, () -> account.debit(new BigDecimal("1000.01")));
        // Balance remains intact
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    @DisplayName("Debit and credit throw on zero or negative amounts")
    void debitAndCreditThrowOnInvalidAmounts() {
        Account account = new Account();

        assertThrows(IllegalArgumentException.class, () -> account.credit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> account.credit(new BigDecimal("-100.00")));
        assertThrows(NullPointerException.class, () -> account.credit(null));

        assertThrows(IllegalArgumentException.class, () -> account.debit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> account.debit(new BigDecimal("-50.00")));
        assertThrows(NullPointerException.class, () -> account.debit(null));
    }

    @Test
    @DisplayName("isSufficientFor boundary tests")
    void isSufficientForBoundaries() {
        Account account = new Account();
        account.credit(new BigDecimal("500.00"));

        assertTrue(account.isSufficientFor(new BigDecimal("500.00"))); // Exact match
        assertTrue(account.isSufficientFor(new BigDecimal("499.99"))); // Less
        assertFalse(account.isSufficientFor(new BigDecimal("500.01"))); // More
        assertFalse(account.isSufficientFor(BigDecimal.ZERO)); // Zero
        assertFalse(account.isSufficientFor(new BigDecimal("-10.00"))); // Negative
        assertFalse(account.isSufficientFor(null)); // Null
    }

    @Test
    @DisplayName("Account status check")
    void accountStatusCheck() {
        Account account = new Account();
        account.setStatus(AccountStatus.SUSPENDED);
        assertFalse(account.isActive());

        account.setStatus(AccountStatus.CLOSED);
        assertFalse(account.isActive());

        account.setStatus(AccountStatus.ACTIVE);
        assertTrue(account.isActive());
    }
}
