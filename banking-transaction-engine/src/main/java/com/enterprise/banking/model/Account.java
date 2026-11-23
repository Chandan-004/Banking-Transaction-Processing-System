package com.enterprise.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a bank account with balance and status.
 * Contains core validation helpers for debit and credit operations,
 * ensuring no negative balance can be created at the entity level.
 */
public class Account {

    private String accountId;
    private String customerId;
    private BigDecimal balance;
    private String currency;
    private AccountStatus status;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Account() {
        this.balance = BigDecimal.ZERO;
        this.currency = "INR";
        this.status = AccountStatus.ACTIVE;
        this.version = 0;
    }

    public Account(String accountId, String customerId, BigDecimal balance, String currency,
                   AccountStatus status, long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "INR";
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Checks if the account is in active status.
     */
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    /**
     * Checks if current balance is sufficient for a debit amount.
     */
    public boolean isSufficientFor(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.balance.compareTo(amount) >= 0;
    }

    /**
     * Debits an amount from balance.
     * Prevents negative balance invariant violation.
     */
    public void debit(BigDecimal amount) {
        Objects.requireNonNull(amount, "Debit amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }
        if (!isSufficientFor(amount)) {
            throw new IllegalStateException("Insufficient balance to execute debit of " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Credits an amount to balance.
     */
    public void credit(BigDecimal amount) {
        Objects.requireNonNull(amount, "Credit amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }
        this.balance = this.balance.add(amount);
    }

    // Getters and Setters
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance != null ? balance : BigDecimal.ZERO;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency != null ? currency : "INR";
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status != null ? status : AccountStatus.ACTIVE;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", balance=" + balance +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                '}';
    }
}
