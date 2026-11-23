package com.enterprise.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a persisted transaction row in the database.
 */
public class TransactionRecord {

    private String transactionId;
    private String sourceAccountId;
    private String destinationAccountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;

    public TransactionRecord() {
    }

    public TransactionRecord(String transactionId, String sourceAccountId, String destinationAccountId,
                             TransactionType transactionType, BigDecimal amount, TransactionStatus status,
                             LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(String sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(String destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
                "transactionId='" + transactionId + '\'' +
                ", sourceAccountId='" + sourceAccountId + '\'' +
                ", destinationAccountId='" + destinationAccountId + '\'' +
                ", transactionType=" + transactionType +
                ", amount=" + amount +
                ", status=" + status +
                '}';
    }
}
