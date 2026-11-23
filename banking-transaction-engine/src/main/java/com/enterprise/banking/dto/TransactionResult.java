package com.enterprise.banking.dto;

import com.enterprise.banking.model.TransactionStatus;

import java.math.BigDecimal;

/**
 * Immutable transaction response DTO using Java 21 record.
 * Contains execution outcome, resulting balances, and duration.
 */
public record TransactionResult(
        String transactionId,
        TransactionStatus status,
        String message,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        long durationMs
) {

    public static TransactionResult success(String txId, String message, BigDecimal sourceBalance,
                                            BigDecimal destinationBalance, long durationMs) {
        return new TransactionResult(txId, TransactionStatus.SUCCESS, message, sourceBalance, destinationBalance, durationMs);
    }

    public static TransactionResult success(String txId, BigDecimal sourceBalance,
                                            BigDecimal destinationBalance, long durationMs) {
        return new TransactionResult(txId, TransactionStatus.SUCCESS, "Transaction completed successfully", sourceBalance, destinationBalance, durationMs);
    }

    public static TransactionResult failed(String txId, String message, long durationMs) {
        return new TransactionResult(txId, TransactionStatus.FAILED, message, null, null, durationMs);
    }

    public static TransactionResult rejected(String txId, String message) {
        return new TransactionResult(txId, TransactionStatus.REJECTED, message, null, null, 0);
    }

    public boolean isSuccess() {
        return this.status == TransactionStatus.SUCCESS;
    }

    public boolean isFailed() {
        return this.status == TransactionStatus.FAILED;
    }

    public boolean isRejected() {
        return this.status == TransactionStatus.REJECTED;
    }
}
