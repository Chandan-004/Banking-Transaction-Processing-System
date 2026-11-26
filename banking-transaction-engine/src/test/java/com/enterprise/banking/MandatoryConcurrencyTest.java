package com.enterprise.banking;

import com.enterprise.banking.concurrency.ConcurrentTransactionProcessor;
import com.enterprise.banking.config.DatabaseConnectionManager;
import com.enterprise.banking.dao.impl.AccountDAOImpl;
import com.enterprise.banking.dao.impl.CustomerDAOImpl;
import com.enterprise.banking.dao.impl.TransactionDAOImpl;
import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.model.Account;
import com.enterprise.banking.model.AccountStatus;
import com.enterprise.banking.model.Customer;
import com.enterprise.banking.model.TransactionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Mandatory Concurrency & Thread-Safety Tests (PDF Section 6.1 & Slide 8)")
class MandatoryConcurrencyTest {

    private DatabaseConnectionManager testConnectionManager;
    private AccountDAOImpl accountDAO;
    private TransactionDAOImpl transactionDAO;
    private CustomerDAOImpl customerDAO;
    private BankingTransactionEngine engine;

    @BeforeEach
    void setUp() throws SQLException {
        String testDbUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        testConnectionManager = DatabaseConnectionManager.createForTest(testDbUrl, "sa", "");

        accountDAO = new AccountDAOImpl();
        transactionDAO = new TransactionDAOImpl();
        customerDAO = new CustomerDAOImpl();
        engine = new BankingTransactionEngine(accountDAO, transactionDAO, testConnectionManager);

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

            customerDAO.save(conn, new Customer("CUST_CONC", "Concurrency Tester", "conc@bank.com", "9999999999", LocalDateTime.now()));
        }
    }

    @AfterEach
    void tearDown() {
        if (testConnectionManager != null) {
            testConnectionManager.close();
        }
    }

    @Test
    @DisplayName("MANDATORY SCENARIO (Slide 8): Balance ₹10,000, concurrent ₹7,000 & ₹5,000 withdrawals - exactly one succeeds")
    void testMandatoryConcurrencyScenario() throws Exception {
        String accountId = "ACC_MANDATORY";

        try (Connection conn = testConnectionManager.getConnection()) {
            accountDAO.save(conn, new Account(accountId, "CUST_CONC", new BigDecimal("10000.00"), "INR",
                    AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
        }

        int workers = 2;
        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, workers, 10)) {
            CountDownLatch readyLatch = new CountDownLatch(workers);
            CountDownLatch startGate = new CountDownLatch(1);

            TransactionRequest reqA = new TransactionRequest("TXN-CONC-7000", accountId, null, new BigDecimal("7000.00"), TransactionType.WITHDRAWAL);
            TransactionRequest reqB = new TransactionRequest("TXN-CONC-5000", accountId, null, new BigDecimal("5000.00"), TransactionType.WITHDRAWAL);

            Callable<TransactionResult> taskA = () -> {
                readyLatch.countDown();
                startGate.await(); // Hold until both threads are ready
                return engine.process(reqA);
            };

            Callable<TransactionResult> taskB = () -> {
                readyLatch.countDown();
                startGate.await(); // Hold until both threads are ready
                return engine.process(reqB);
            };

            ExecutorService executor = Executors.newFixedThreadPool(workers);
            Future<TransactionResult> futureA = executor.submit(taskA);
            Future<TransactionResult> futureB = executor.submit(taskB);

            // Wait until both threads are primed, then release both at the exact same microsecond
            readyLatch.await(5, TimeUnit.SECONDS);
            startGate.countDown();

            TransactionResult resultA = futureA.get(10, TimeUnit.SECONDS);
            TransactionResult resultB = futureB.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            int successCount = 0;
            int failedCount = 0;
            if (resultA.isSuccess()) successCount++; else failedCount++;
            if (resultB.isSuccess()) successCount++; else failedCount++;

            // Invariant 1: Exactly ONE withdrawal succeeds
            assertEquals(1, successCount, "Exactly one withdrawal must succeed");
            // Invariant 2: Exactly ONE withdrawal fails
            assertEquals(1, failedCount, "Exactly one withdrawal must fail with insufficient funds");

            // Invariant 3: Final balance must be >= 0 and equal to remaining amount (either 3000.00 or 5000.00)
            try (Connection conn = testConnectionManager.getConnection()) {
                Account finalAccount = accountDAO.findById(conn, accountId).orElseThrow();
                BigDecimal finalBalance = finalAccount.getBalance();

                assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0, "Final balance must never be negative");
                assertTrue(
                        finalBalance.compareTo(new BigDecimal("3000.00")) == 0 ||
                        finalBalance.compareTo(new BigDecimal("5000.00")) == 0,
                        "Final balance must be either 3000.00 or 5000.00, but was: " + finalBalance
                );
            }
        }
    }

    @Test
    @DisplayName("20 WORKER THREADS: Simultaneous deposits and withdrawals preserve balance with zero lost updates")
    void testTwentyThreadsConcurrentHammering() throws Exception {
        String accountId = "ACC_STRESS_20";

        try (Connection conn = testConnectionManager.getConnection()) {
            accountDAO.save(conn, new Account(accountId, "CUST_CONC", new BigDecimal("20000.00"), "INR",
                    AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
        }

        int workerThreads = 20;
        int operationsPerType = 20; // 20 deposits + 20 withdrawals = 40 total concurrent operations

        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, workerThreads, 100)) {
            List<TransactionRequest> requests = new ArrayList<>();

            for (int i = 0; i < operationsPerType; i++) {
                requests.add(new TransactionRequest("TXN-DEP-" + i, null, accountId, new BigDecimal("500.00"), TransactionType.DEPOSIT));
                requests.add(new TransactionRequest("TXN-WTH-" + i, accountId, null, new BigDecimal("500.00"), TransactionType.WITHDRAWAL));
            }

            // Execute all 40 operations concurrently across 20 worker threads
            List<TransactionResult> results = processor.processBatch(requests);

            assertEquals(40, results.size());
            for (TransactionResult res : results) {
                assertTrue(res.isSuccess(), "Each operation in stress test must succeed");
            }

            // Invariant: Balance started at 20,000, added 20*500 (10k), subtracted 20*500 (10k). Final MUST be 20,000.
            try (Connection conn = testConnectionManager.getConnection()) {
                Account finalAccount = accountDAO.findById(conn, accountId).orElseThrow();
                assertEquals(new BigDecimal("20000.00"), finalAccount.getBalance(),
                        "No lost updates: final balance must equal starting balance exactly");
            }
        }
    }

    @Test
    @DisplayName("DEADLOCK FREEDOM: 20 threads cross-transferring between Account A and B never deadlock")
    void testCrossTransfersDeadlockFreedom() throws Exception {
        String accA = "ACC_A";
        String accB = "ACC_B";

        try (Connection conn = testConnectionManager.getConnection()) {
            accountDAO.save(conn, new Account(accA, "CUST_CONC", new BigDecimal("50000.00"), "INR",
                    AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
            accountDAO.save(conn, new Account(accB, "CUST_CONC", new BigDecimal("50000.00"), "INR",
                    AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
        }

        int workerThreads = 20;
        int transfersEachWay = 15; // 15 A->B and 15 B->A = 30 total cross transfers

        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, workerThreads, 100)) {
            List<TransactionRequest> requests = new ArrayList<>();

            for (int i = 0; i < transfersEachWay; i++) {
                // A -> B
                requests.add(new TransactionRequest("TXN-XFER-AB-" + i, accA, accB, new BigDecimal("100.00"), TransactionType.TRANSFER));
                // B -> A (Opposite direction concurrently)
                requests.add(new TransactionRequest("TXN-XFER-BA-" + i, accB, accA, new BigDecimal("100.00"), TransactionType.TRANSFER));
            }

            // Must finish cleanly without deadlocking
            List<TransactionResult> results = processor.processBatch(requests);

            assertEquals(30, results.size());
            for (TransactionResult res : results) {
                assertTrue(res.isSuccess(), "Each cross-transfer must succeed without deadlock");
            }

            // Money conservation invariant: Total money in bank must still be 100,000.00
            try (Connection conn = testConnectionManager.getConnection()) {
                Account finalA = accountDAO.findById(conn, accA).orElseThrow();
                Account finalB = accountDAO.findById(conn, accB).orElseThrow();

                BigDecimal totalBalance = finalA.getBalance().add(finalB.getBalance());
                assertEquals(new BigDecimal("100000.00"), totalBalance, "Total money across both accounts must be strictly conserved");
            }
        }
    }
}
