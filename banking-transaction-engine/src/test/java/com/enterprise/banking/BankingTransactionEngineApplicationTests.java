package com.enterprise.banking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Pure JUnit 5 sanity test verifying the Core Java environment setup.
 */
class BankingTransactionEngineApplicationTests {

    @Test
    @DisplayName("Verify Core Java application main method executes cleanly")
    void mainMethodStartsWithoutException() {
        assertDoesNotThrow(() -> BankingTransactionEngineApplication.main(new String[]{}));
    }
}
