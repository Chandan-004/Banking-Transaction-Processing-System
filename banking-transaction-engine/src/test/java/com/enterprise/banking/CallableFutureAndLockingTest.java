package com.enterprise.banking;

import com.enterprise.banking.concurrency.AccountLockManager;
import com.enterprise.banking.concurrency.ConcurrentTransactionProcessor;
import com.enterprise.banking.concurrency.TransactionTask;
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

@DisplayName("Callable, Future, Synchronization & Locking Tests (PDF Section 6.1)")
class CallableFutureAndLockingTest {

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

            customerDAO.save(conn, new Customer("CUST_LOCK", "Lock Tester", "lock@bank.com", "8888888888", LocalDateTime.now()));
            accountDAO.save(conn, new Account("ACC_LOCK_1", "CUST_LOCK", new BigDecimal("10000.00"), "INR", AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
            accountDAO.save(conn, new Account("ACC_LOCK_2", "CUST_LOCK", new BigDecimal("10000.00"), "INR", AccountStatus.ACTIVE, 0, LocalDateTime.now(), LocalDateTime.now()));
        }
    }

    @AfterEach
    void tearDown() {
        if (testConnectionManager != null) {
            testConnectionManager.close();
        }
    }

    @Test
    @DisplayName("CALLABLE & FUTURE: Execute TransactionTask Callable and retrieve outcome via Future.get()")
    void testCallableAndFutureSubmission() throws Exception {
        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, 4, 10)) {
            TransactionRequest req = new TransactionRequest("TXN-CALLABLE-1", null, "ACC_LOCK_1", new BigDecimal("1500.00"), TransactionType.DEPOSIT);
            TransactionTask task = new TransactionTask(engine, req);

            Future<TransactionResult> future = processor.submit(task);
            TransactionResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(future.isDone());
            assertTrue(result.isSuccess());
            assertEquals(new BigDecimal("11500.00"), result.destinationBalance());
        }
    }

    @Test
    @DisplayName("INVOKE ALL: Submit list of Callables and verify all Futures resolve")
    void testInvokeAllWithListOfCallables() throws Exception {
        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, 5, 20)) {
            List<Callable<TransactionResult>> tasks = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                TransactionRequest req = new TransactionRequest("TXN-BATCH-" + i, null, "ACC_LOCK_1", new BigDecimal("100.00"), TransactionType.DEPOSIT);
                tasks.add(new TransactionTask(engine, req));
            }

            List<Future<TransactionResult>> futures = processor.invokeAll(tasks);
            assertEquals(5, futures.size());

            for (Future<TransactionResult> future : futures) {
                assertTrue(future.isDone());
                assertTrue(future.get().isSuccess());
            }

            // 10,000 + 5 * 100 = 10,500
            try (Connection conn = testConnectionManager.getConnection()) {
                Account acc = accountDAO.findById(conn, "ACC_LOCK_1").orElseThrow();
                assertEquals(new BigDecimal("10500.00"), acc.getBalance());
            }
        }
    }

    @Test
    @DisplayName("ACCOUNT LOCK MANAGER: Synchronized execution prevents race conditions on hot account")
    void testRaceConditionPreventionOnSingleAccount() throws Exception {
        int threadCount = 10;
        int operations = 20; // 10 deposits and 10 withdrawals

        try (ConcurrentTransactionProcessor processor = new ConcurrentTransactionProcessor(engine, threadCount, 50)) {
            List<Future<TransactionResult>> futures = new ArrayList<>();

            for (int i = 0; i < operations / 2; i++) {
                // Deposit 200
                futures.add(processor.submitCallable(new TransactionRequest(
                        "TXN-RACE-DEP-" + i, null, "ACC_LOCK_1", new BigDecimal("200.00"), TransactionType.DEPOSIT)));
                // Withdraw 200
                futures.add(processor.submitCallable(new TransactionRequest(
                        "TXN-RACE-WTH-" + i, "ACC_LOCK_1", null, new BigDecimal("200.00"), TransactionType.WITHDRAWAL)));
            }

            // Wait for all futures to resolve
            for (Future<TransactionResult> future : futures) {
                TransactionResult res = future.get(10, TimeUnit.SECONDS);
                assertTrue(res.isSuccess());
            }

            // Net balance must remain unchanged: 10,000.00 (Zero race conditions, zero lost updates)
            try (Connection conn = testConnectionManager.getConnection()) {
                Account acc = accountDAO.findById(conn, "ACC_LOCK_1").orElseThrow();
                assertEquals(new BigDecimal("10000.00"), acc.getBalance(),
                        "Balance must be preserved exactly with zero lost updates under concurrent lock synchronization");
            }
        }
    }

    @Test
    @DisplayName("DETERMINISTIC DUAL LOCKING: Reciprocal transfers between ACC_LOCK_1 and ACC_LOCK_2 never deadlock")
    void testDeterministicDualLockingDeadlockFreedom() throws Exception {
        AccountLockManager lockManager = new AccountLockManager();

        int workers = 10;
        int transfers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < transfers / 2; i++) {
            // Thread A: locks ACC_LOCK_1 and ACC_LOCK_2
            futures.add(executor.submit(() ->
                lockManager.executeWithDualLocks("ACC_LOCK_1", "ACC_LOCK_2", () -> "1->2 SUCCESS")
            ));

            // Thread B: locks ACC_LOCK_2 and ACC_LOCK_1 in opposite argument order
            futures.add(executor.submit(() ->
                lockManager.executeWithDualLocks("ACC_LOCK_2", "ACC_LOCK_1", () -> "2->1 SUCCESS")
            ));
        }

        for (Future<String> future : futures) {
            String result = future.get(5, TimeUnit.SECONDS);
            assertTrue(result.contains("SUCCESS"));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}
