package com.enterprise.banking;

import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.model.TransactionStatus;
import com.enterprise.banking.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transaction DTO Unit Tests")
class TransactionDtoTest {

    @Test
    @DisplayName("TransactionRequest record trims whitespace safely")
    void transactionRequestTrimsInputs() {
        TransactionRequest req = new TransactionRequest(
                "  TXN1001  ",
                " ACC01 ",
                " ACC02 ",
                new BigDecimal("500.00"),
                TransactionType.TRANSFER
        );

        assertEquals("TXN1001", req.transactionId());
        assertEquals("ACC01", req.sourceAccount());
        assertEquals("ACC02", req.destinationAccount());
        assertEquals(new BigDecimal("500.00"), req.amount());
        assertEquals(TransactionType.TRANSFER, req.type());
    }

    @Test
    @DisplayName("TransactionResult factory methods build correct status and outcomes")
    void transactionResultFactories() {
        // Success
        TransactionResult success = TransactionResult.success(
                "TXN1",
                new BigDecimal("3000.00"),
                new BigDecimal("7000.00"),
                5L
        );
        assertTrue(success.isSuccess());
        assertFalse(success.isFailed());
        assertFalse(success.isRejected());
        assertEquals(TransactionStatus.SUCCESS, success.status());
        assertEquals(new BigDecimal("3000.00"), success.sourceBalance());
        assertEquals(new BigDecimal("7000.00"), success.destinationBalance());
        assertEquals(5L, success.durationMs());

        // Failed
        TransactionResult failed = TransactionResult.failed("TXN2", "Insufficient balance", 3L);
        assertFalse(failed.isSuccess());
        assertTrue(failed.isFailed());
        assertFalse(failed.isRejected());
        assertEquals(TransactionStatus.FAILED, failed.status());

        // Rejected
        TransactionResult rejected = TransactionResult.rejected("TXN3", "Account not found");
        assertFalse(rejected.isSuccess());
        assertFalse(rejected.isFailed());
        assertTrue(rejected.isRejected());
        assertEquals(TransactionStatus.REJECTED, rejected.status());
    }
}
