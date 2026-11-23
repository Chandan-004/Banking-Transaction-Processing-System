package com.enterprise.banking.model;

import java.time.LocalDateTime;

/**
 * Represents failure and rejection diagnostics stored in the database.
 */
public class FailedTransaction {

    private Long failureId;
    private String transactionId;
    private ErrorCategory errorCategory;
    private String failureReason;
    private LocalDateTime failedAt;

    public FailedTransaction() {
    }

    public FailedTransaction(Long failureId, String transactionId, ErrorCategory errorCategory,
                             String failureReason, LocalDateTime failedAt) {
        this.failureId = failureId;
        this.transactionId = transactionId;
        this.errorCategory = errorCategory;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
    }

    public Long getFailureId() {
        return failureId;
    }

    public void setFailureId(Long failureId) {
        this.failureId = failureId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(ErrorCategory errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    @Override
    public String toString() {
        return "FailedTransaction{" +
                "failureId=" + failureId +
                ", transactionId='" + transactionId + '\'' +
                ", errorCategory=" + errorCategory +
                ", failureReason='" + failureReason + '\'' +
                '}';
    }
}
