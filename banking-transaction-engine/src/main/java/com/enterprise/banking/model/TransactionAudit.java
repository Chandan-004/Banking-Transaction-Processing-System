package com.enterprise.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the immutable audit trail row for every processed transaction.
 * Records financial state before and after execution, thread name, and latency.
 */
public class TransactionAudit {

    private Long auditId;
    private String transactionId;
    private BigDecimal sourceBalanceBefore;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal destinationBalanceBefore;
    private BigDecimal destinationBalanceAfter;
    private String executionThread;
    private long durationMs;
    private LocalDateTime auditedAt;

    public TransactionAudit() {
    }

    public TransactionAudit(Long auditId, String transactionId, BigDecimal sourceBalanceBefore,
                            BigDecimal sourceBalanceAfter, BigDecimal destinationBalanceBefore,
                            BigDecimal destinationBalanceAfter, String executionThread,
                            long durationMs, LocalDateTime auditedAt) {
        this.auditId = auditId;
        this.transactionId = transactionId;
        this.sourceBalanceBefore = sourceBalanceBefore;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.destinationBalanceBefore = destinationBalanceBefore;
        this.destinationBalanceAfter = destinationBalanceAfter;
        this.executionThread = executionThread;
        this.durationMs = durationMs;
        this.auditedAt = auditedAt;
    }

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getSourceBalanceBefore() {
        return sourceBalanceBefore;
    }

    public void setSourceBalanceBefore(BigDecimal sourceBalanceBefore) {
        this.sourceBalanceBefore = sourceBalanceBefore;
    }

    public BigDecimal getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) {
        this.sourceBalanceAfter = sourceBalanceAfter;
    }

    public BigDecimal getDestinationBalanceBefore() {
        return destinationBalanceBefore;
    }

    public void setDestinationBalanceBefore(BigDecimal destinationBalanceBefore) {
        this.destinationBalanceBefore = destinationBalanceBefore;
    }

    public BigDecimal getDestinationBalanceAfter() {
        return destinationBalanceAfter;
    }

    public void setDestinationBalanceAfter(BigDecimal destinationBalanceAfter) {
        this.destinationBalanceAfter = destinationBalanceAfter;
    }

    public String getExecutionThread() {
        return executionThread;
    }

    public void setExecutionThread(String executionThread) {
        this.executionThread = executionThread;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getAuditedAt() {
        return auditedAt;
    }

    public void setAuditedAt(LocalDateTime auditedAt) {
        this.auditedAt = auditedAt;
    }

    @Override
    public String toString() {
        return "TransactionAudit{" +
                "auditId=" + auditId +
                ", transactionId='" + transactionId + '\'' +
                ", sourceBalanceBefore=" + sourceBalanceBefore +
                ", sourceBalanceAfter=" + sourceBalanceAfter +
                ", durationMs=" + durationMs +
                '}';
    }
}
