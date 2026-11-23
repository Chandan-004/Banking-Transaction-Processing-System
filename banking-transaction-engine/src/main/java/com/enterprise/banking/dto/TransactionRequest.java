package com.enterprise.banking.dto;

import com.enterprise.banking.model.TransactionType;

import java.math.BigDecimal;

/**
 * Immutable transaction request DTO using Java 21 record.
 * Safe to pass across multiple threads without risk of mutation.
 */
public record TransactionRequest(
        String transactionId,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        TransactionType type
) {
    public TransactionRequest {
        if (transactionId != null) {
            transactionId = transactionId.trim();
        }
        if (sourceAccount != null) {
            sourceAccount = sourceAccount.trim();
        }
        if (destinationAccount != null) {
            destinationAccount = destinationAccount.trim();
        }
    }
}
