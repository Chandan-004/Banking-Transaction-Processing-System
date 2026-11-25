package com.enterprise.banking;

import com.enterprise.banking.config.DatabaseConnectionManager;
import com.enterprise.banking.dao.impl.AccountDAOImpl;
import com.enterprise.banking.dao.impl.CustomerDAOImpl;
import com.enterprise.banking.dao.impl.TransactionDAOImpl;
import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.model.*;
import com.enterprise.banking.service.BankingTransactionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BankingTransactionEngine Integration Tests (Deposit, Withdraw, Transfer & JDBC SQL)")
class BankingTransactionEngineTest {

    private DatabaseConnectionManager testConnectionManager;
    private AccountDAOImpl accountDAO;
    private TransactionDAOImpl transactionDAO;
    private CustomerDAOImpl customerDAO;
    private BankingTransactionEngine engine;

    @BeforeEach
    void setUp() throws SQLException {
        // Spin up isolated in-memory H2 database with MySQL compatibility
        String testDbUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        testConnectionManager = DatabaseConnectionManager.createForTest(testDbUrl, "sa", "");

        accountDAO = new AccountDAOImpl();
        transactionDAO = new TransactionDAOImpl();
        customerDAO = new CustomerDAOImpl();
        engine = new BankingTransactionEngine(accountDAO, transactionDAO, testConnectionManager);

        // Initialize schema
        try (Connection conn = testConnectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE customer (
                    customer_id VARCHAR(36) PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) NOT NULL UNIQUE,
                    phone_number VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.execute("""
                CREATE TABLE account (
                    account_id VARCHAR(36) PRIMARY KEY,
                    customer_id VARCHAR(36) NOT NULL,
                    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                    currency VARCHAR(3) DEFAULT 'INR',
                    status VARCHAR(20) DEFAULT 'ACTIVE',
                    version BIGINT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT chk_bal CHECK (balance >= 0.00)
                );
            """);

            stmt.execute("""
                CREATE TABLE transaction (
                    transaction_id VARCHAR(64) PRIMARY KEY,
                    source_account_id VARCHAR(36),
                    destination_account_id VARCHAR(36),
                    transaction_type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15,2) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.execute("""
                CREATE TABLE transaction_audit (
                    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    transaction_id VARCHAR(64) NOT NULL,
                    source_balance_before DECIMAL(15,2),
                    source_balance_after DECIMAL(15,2),
                    destination_balance_before DECIMAL(15,2),
                    destination_balance_after DECIMAL(15,2),
                    execution_thread VARCHAR(64) NOT NULL,
                    duration_ms BIGINT NOT NULL,
                    audited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.execute("""
                CREATE TABLE failed_transaction (
                    failure_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    transaction_id VARCHAR(64) NOT NULL,
                    error_category VARCHAR(50) NOT NULL,
                    failure_reason TEXT NOT NULL,
                    failed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Seed test customer
            customerDAO.save(conn, new Customer("CUST1", "John Doe", "john@bank.com", "9876543210", LocalDateTime.now()));

            // Seed test accounts
            accountDAO.save(conn, new Account("ACC1", "CUST1", new BigDecimal("10000.00"), "INR", AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
            accountDAO.save(conn, new Account("ACC2", "CUST1", new BigDecimal("5000.00"), "INR", AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
            accountDAO.save(conn, new Account("ACC_INACTIVE", "CUST1", new BigDecimal("1000.00"), "INR", AccountStatus.SUSPENDED, 0, LocalDateTime.now(), LocalDateTime.now()));
        }
    }

    @AfterEach
    void tearDown() {
        if (testConnectionManager != null) {
            testConnectionManager.close();
        }
    }

    @Test
    @DisplayName("DEPOSIT: Credits funds to active account and records audit successfully")
    void testDepositSuccess() throws SQLException {
        TransactionRequest req = new TransactionRequest("TXN-DEP-1", null, "ACC1", new BigDecimal("2500.00"), TransactionType.DEPOSIT);
        TransactionResult res = engine.process(req);

        assertTrue(res.isSuccess());
        assertEquals(TransactionStatus.SUCCESS, res.status());
        assertEquals(new BigDecimal("12500.00"), res.destinationBalance());

        // Verify database state
        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc = accountDAO.findById(conn, "ACC1").orElseThrow();
            assertEquals(new BigDecimal("12500.00"), acc.getBalance());
        }
    }

    @Test
    @DisplayName("DEPOSIT: Rejects zero or negative amounts at early validation gate")
    void testDepositRejectsInvalidAmount() {
        TransactionRequest req = new TransactionRequest("TXN-DEP-2", null, "ACC1", new BigDecimal("-100.00"), TransactionType.DEPOSIT);
        TransactionResult res = engine.process(req);

        assertTrue(res.isRejected());
        assertEquals(TransactionStatus.REJECTED, res.status());
    }

    @Test
    @DisplayName("DEPOSIT: Rejects non-existent destination account")
    void testDepositRejectsMissingAccount() {
        TransactionRequest req = new TransactionRequest("TXN-DEP-3", null, "NON_EXISTENT", new BigDecimal("100.00"), TransactionType.DEPOSIT);
        TransactionResult res = engine.process(req);

        assertTrue(res.isRejected());
        assertEquals(TransactionStatus.REJECTED, res.status());
    }

    @Test
    @DisplayName("WITHDRAWAL: Debits funds from sufficiently funded active account")
    void testWithdrawalSuccess() throws SQLException {
        TransactionRequest req = new TransactionRequest("TXN-WTH-1", "ACC1", null, new BigDecimal("4000.00"), TransactionType.WITHDRAWAL);
        TransactionResult res = engine.process(req);

        assertTrue(res.isSuccess());
        assertEquals(new BigDecimal("6000.00"), res.sourceBalance());

        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc = accountDAO.findById(conn, "ACC1").orElseThrow();
            assertEquals(new BigDecimal("6000.00"), acc.getBalance());
        }
    }

    @Test
    @DisplayName("WITHDRAWAL: Fails when balance is insufficient; rolls back and preserves original balance")
    void testWithdrawalInsufficientBalance() throws SQLException {
        TransactionRequest req = new TransactionRequest("TXN-WTH-2", "ACC1", null, new BigDecimal("15000.00"), TransactionType.WITHDRAWAL);
        TransactionResult res = engine.process(req);

        assertTrue(res.isFailed());
        assertEquals(TransactionStatus.FAILED, res.status());

        // Verify rollback invariant: balance must still be 10,000
        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc = accountDAO.findById(conn, "ACC1").orElseThrow();
            assertEquals(new BigDecimal("10000.00"), acc.getBalance());
        }
    }

    @Test
    @DisplayName("WITHDRAWAL: Rejects withdrawal on SUSPENDED account")
    void testWithdrawalInactiveAccount() {
        TransactionRequest req = new TransactionRequest("TXN-WTH-3", "ACC_INACTIVE", null, new BigDecimal("500.00"), TransactionType.WITHDRAWAL);
        TransactionResult res = engine.process(req);

        assertTrue(res.isRejected());
        assertEquals(TransactionStatus.REJECTED, res.status());
    }

    @Test
    @DisplayName("TRANSFER: Moves money between two accounts atomically and preserves total bank balance")
    void testTransferSuccess() throws SQLException {
        // ACC1 (10,000) -> ACC2 (5,000) transfer 3,000
        TransactionRequest req = new TransactionRequest("TXN-TRF-1", "ACC1", "ACC2", new BigDecimal("3000.00"), TransactionType.TRANSFER);
        TransactionResult res = engine.process(req);

        assertTrue(res.isSuccess());
        assertEquals(new BigDecimal("7000.00"), res.sourceBalance());
        assertEquals(new BigDecimal("8000.00"), res.destinationBalance());

        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc1 = accountDAO.findById(conn, "ACC1").orElseThrow();
            Account acc2 = accountDAO.findById(conn, "ACC2").orElseThrow();

            assertEquals(new BigDecimal("7000.00"), acc1.getBalance());
            assertEquals(new BigDecimal("8000.00"), acc2.getBalance());
            // Total money invariant: 7,000 + 8,000 == 15,000
            assertEquals(new BigDecimal("15000.00"), acc1.getBalance().add(acc2.getBalance()));
        }
    }

    @Test
    @DisplayName("TRANSFER: Fails on insufficient balance and rolls back both accounts")
    void testTransferFailsInsufficientBalance() throws SQLException {
        TransactionRequest req = new TransactionRequest("TXN-TRF-2", "ACC2", "ACC1", new BigDecimal("99999.00"), TransactionType.TRANSFER);
        TransactionResult res = engine.process(req);

        assertTrue(res.isFailed());

        // Both accounts must remain completely unchanged
        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc1 = accountDAO.findById(conn, "ACC1").orElseThrow();
            Account acc2 = accountDAO.findById(conn, "ACC2").orElseThrow();

            assertEquals(new BigDecimal("10000.00"), acc1.getBalance());
            assertEquals(new BigDecimal("5000.00"), acc2.getBalance());
        }
    }

    @Test
    @DisplayName("TRANSFER: Rejects transfer between identical source and destination accounts")
    void testTransferSelfTransferRejected() {
        TransactionRequest req = new TransactionRequest("TXN-TRF-3", "ACC1", "ACC1", new BigDecimal("500.00"), TransactionType.TRANSFER);
        TransactionResult res = engine.process(req);

        assertTrue(res.isRejected());
    }

    @Test
    @DisplayName("IDEMPOTENCY: Repeated transaction ID is rejected and does not double-charge balance")
    void testIdempotencyDuplicateRejected() throws SQLException {
        TransactionRequest req1 = new TransactionRequest("TXN-DUP-TEST", "ACC1", null, new BigDecimal("1000.00"), TransactionType.WITHDRAWAL);
        TransactionResult res1 = engine.process(req1);
        assertTrue(res1.isSuccess());

        // Resubmit the exact same transaction ID
        TransactionRequest req2 = new TransactionRequest("TXN-DUP-TEST", "ACC1", null, new BigDecimal("1000.00"), TransactionType.WITHDRAWAL);
        TransactionResult res2 = engine.process(req2);
        assertTrue(res2.isRejected());

        // Balance must only have been debited ONCE: 10,000 - 1,000 = 9,000 (NOT 8,000)
        try (Connection conn = testConnectionManager.getConnection()) {
            Account acc1 = accountDAO.findById(conn, "ACC1").orElseThrow();
            assertEquals(new BigDecimal("9000.00"), acc1.getBalance());
        }
    }
}
