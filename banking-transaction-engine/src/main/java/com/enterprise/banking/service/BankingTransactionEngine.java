package com.enterprise.banking.service;

import com.enterprise.banking.config.DatabaseConnectionManager;
import com.enterprise.banking.dao.AccountDAO;
import com.enterprise.banking.dao.TransactionDAO;
import com.enterprise.banking.dao.impl.AccountDAOImpl;
import com.enterprise.banking.dao.impl.TransactionDAOImpl;
import com.enterprise.banking.dto.TransactionRequest;
import com.enterprise.banking.dto.TransactionResult;
import com.enterprise.banking.exception.*;
import com.enterprise.banking.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Core Banking Transaction Processing Engine.
 * Pure Core Java implementation managing Deposit, Withdrawal, and Transfer.
 * Enforces:
 * 1. Strict 7-step transaction lifecycle.
 * 2. Explicit JDBC transaction boundaries (setAutoCommit(false), commit, rollback).
 * 3. Pessimistic row locking (SELECT ... FOR UPDATE).
 * 4. Deterministic lock ordering for transfers (prevents deadlocks).
 * 5. Idempotency guarantees (duplicate requests do not double impact balances).
 * 6. Structured logging with SLF4J MDC and persistent database audit trails.
 */
public class BankingTransactionEngine {

    private static final Logger logger = LoggerFactory.getLogger(BankingTransactionEngine.class);

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;
    private final DatabaseConnectionManager connectionManager;

    public BankingTransactionEngine() {
        this(new AccountDAOImpl(), new TransactionDAOImpl(), DatabaseConnectionManager.getInstance());
    }

    public BankingTransactionEngine(AccountDAO accountDAO, TransactionDAO transactionDAO, DatabaseConnectionManager connectionManager) {
        this.accountDAO = Objects.requireNonNull(accountDAO, "accountDAO cannot be null");
        this.transactionDAO = Objects.requireNonNull(transactionDAO, "transactionDAO cannot be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager cannot be null");
    }

    /**
     * Unified entry point. Processes Deposit, Withdrawal, or Transfer.
     */
    public TransactionResult process(TransactionRequest request) {
        long startTime = System.currentTimeMillis();

        if (request == null) {
            return TransactionResult.rejected("UNKNOWN", "Transaction request cannot be null");
        }

        setupMdc(request);
        try {
            // Step 1: Early Validation Gate
            validateRequest(request);

            // Step 2 & 3: Idempotency Gate & Monetary Movement Execution
            return executeTransaction(request, startTime);

        } catch (InvalidTransactionException | AccountNotFoundException | AccountInactiveException ex) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("Transaction [{}] REJECTED: {}", request.transactionId(), ex.getMessage());
            recordRejectionOrFailure(request, TransactionStatus.REJECTED, ex.getErrorCategory(), ex.getMessage());
            return TransactionResult.rejected(request.transactionId(), ex.getMessage());

        } catch (InsufficientBalanceException ex) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("Transaction [{}] FAILED: {}", request.transactionId(), ex.getMessage());
            recordRejectionOrFailure(request, TransactionStatus.FAILED, ex.getErrorCategory(), ex.getMessage());
            return TransactionResult.failed(request.transactionId(), ex.getMessage(), duration);

        } catch (DuplicateTransactionException ex) {
            logger.warn("Transaction [{}] DUPLICATE DETECTED: {}", request.transactionId(), ex.getMessage());
            return TransactionResult.rejected(request.transactionId(), ex.getMessage());

        } catch (SQLException ex) {
            long duration = System.currentTimeMillis() - startTime;
            if (isDuplicateKeyViolation(ex)) {
                logger.warn("Transaction [{}] DB UNIQUE CONSTRAINT DUPLICATE DETECTED: {}", request.transactionId(), ex.getMessage());
                return TransactionResult.rejected(request.transactionId(), "Duplicate transaction ID (database constraint violation): " + request.transactionId());
            }
            logger.error("Transaction [{}] SQL ERROR: {}", request.transactionId(), ex.getMessage(), ex);
            recordRejectionOrFailure(request, TransactionStatus.FAILED, ErrorCategory.SYSTEM_ERROR, ex.getMessage());
            return TransactionResult.failed(request.transactionId(), "Database error: " + ex.getMessage(), duration);

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Transaction [{}] SYSTEM ERROR: {}", request.transactionId(), ex.getMessage(), ex);
            recordRejectionOrFailure(request, TransactionStatus.FAILED, ErrorCategory.SYSTEM_ERROR, ex.getMessage());
            return TransactionResult.failed(request.transactionId(), "System error: " + ex.getMessage(), duration);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Executes Deposit within an explicit JDBC transaction.
     */
    public TransactionResult deposit(TransactionRequest request) {
        if (request.type() != TransactionType.DEPOSIT) {
            throw new InvalidTransactionException("Expected DEPOSIT transaction type");
        }
        return process(request);
    }

    /**
     * Executes Withdrawal within an explicit JDBC transaction.
     */
    public TransactionResult withdraw(TransactionRequest request) {
        if (request.type() != TransactionType.WITHDRAWAL) {
            throw new InvalidTransactionException("Expected WITHDRAWAL transaction type");
        }
        return process(request);
    }

    /**
     * Executes Transfer within an explicit JDBC transaction with deterministic locking.
     */
    public TransactionResult transfer(TransactionRequest request) {
        if (request.type() != TransactionType.TRANSFER) {
            throw new InvalidTransactionException("Expected TRANSFER transaction type");
        }
        return process(request);
    }

    // =========================================================================
    // Internal Transaction Lifecycle
    // =========================================================================

    private TransactionResult executeTransaction(TransactionRequest request, long startTime) throws SQLException {
        try (Connection conn = connectionManager.getConnection()) {

            // Step 2: Idempotency Check
            if (transactionDAO.isDuplicate(conn, request.transactionId())) {
                throw new DuplicateTransactionException("Duplicate transaction ID already processed: " + request.transactionId());
            }

            // Step 3: Begin explicit transaction boundary
            conn.setAutoCommit(false);
            try {
                TransactionResult result = switch (request.type()) {
                    case DEPOSIT -> executeDeposit(conn, request, startTime);
                    case WITHDRAWAL -> executeWithdrawal(conn, request, startTime);
                    case TRANSFER -> executeTransfer(conn, request, startTime);
                };

                // Step 6: Commit transaction
                conn.commit();
                logger.info("Transaction [{}] committed successfully in {} ms", request.transactionId(), result.durationMs());
                return result;

            } catch (Exception ex) {
                // Rollback on any failure
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private TransactionResult executeDeposit(Connection conn, TransactionRequest req, long startTime) throws SQLException {
        String targetAccountId = req.destinationAccount() != null ? req.destinationAccount() : req.sourceAccount();
        if (targetAccountId == null || targetAccountId.isBlank()) {
            throw new InvalidTransactionException("Target account ID required for DEPOSIT");
        }

        // Step 4: Lock row
        Account account = accountDAO.findByIdForUpdate(conn, targetAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + targetAccountId));

        if (!account.isActive()) {
            throw new AccountInactiveException("Account is not active: " + targetAccountId);
        }

        // Step 5: Apply operation
        BigDecimal balanceBefore = account.getBalance();
        account.credit(req.amount());
        BigDecimal balanceAfter = account.getBalance();
        accountDAO.updateBalance(conn, targetAccountId, balanceAfter);

        long duration = System.currentTimeMillis() - startTime;

        // Record Transaction
        TransactionRecord record = new TransactionRecord(
                req.transactionId(), null, targetAccountId, TransactionType.DEPOSIT,
                req.amount(), TransactionStatus.SUCCESS, LocalDateTime.now()
        );
        transactionDAO.saveTransaction(conn, record);

        // Record Audit
        TransactionAudit audit = new TransactionAudit(
                null, req.transactionId(), null, null,
                balanceBefore, balanceAfter, Thread.currentThread().getName(), duration, LocalDateTime.now()
        );
        transactionDAO.saveAudit(conn, audit);

        return TransactionResult.success(req.transactionId(), "Deposit successful", null, balanceAfter, duration);
    }

    private TransactionResult executeWithdrawal(Connection conn, TransactionRequest req, long startTime) throws SQLException {
        String sourceAccountId = req.sourceAccount();
        if (sourceAccountId == null || sourceAccountId.isBlank()) {
            throw new InvalidTransactionException("Source account ID required for WITHDRAWAL");
        }

        // Step 4: Lock row
        Account account = accountDAO.findByIdForUpdate(conn, sourceAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + sourceAccountId));

        if (!account.isActive()) {
            throw new AccountInactiveException("Account is not active: " + sourceAccountId);
        }

        // Check sufficiency
        if (!account.isSufficientFor(req.amount())) {
            throw new InsufficientBalanceException("Insufficient balance in account " + sourceAccountId +
                    ". Available: " + account.getBalance() + ", Requested: " + req.amount());
        }

        // Step 5: Apply operation
        BigDecimal balanceBefore = account.getBalance();
        account.debit(req.amount());
        BigDecimal balanceAfter = account.getBalance();
        accountDAO.updateBalance(conn, sourceAccountId, balanceAfter);

        long duration = System.currentTimeMillis() - startTime;

        // Record Transaction
        TransactionRecord record = new TransactionRecord(
                req.transactionId(), sourceAccountId, null, TransactionType.WITHDRAWAL,
                req.amount(), TransactionStatus.SUCCESS, LocalDateTime.now()
        );
        transactionDAO.saveTransaction(conn, record);

        // Record Audit
        TransactionAudit audit = new TransactionAudit(
                null, req.transactionId(), balanceBefore, balanceAfter,
                null, null, Thread.currentThread().getName(), duration, LocalDateTime.now()
        );
        transactionDAO.saveAudit(conn, audit);

        return TransactionResult.success(req.transactionId(), "Withdrawal successful", balanceAfter, null, duration);
    }

    private TransactionResult executeTransfer(Connection conn, TransactionRequest req, long startTime) throws SQLException {
        String srcId = req.sourceAccount();
        String dstId = req.destinationAccount();

        if (srcId == null || srcId.isBlank() || dstId == null || dstId.isBlank()) {
            throw new InvalidTransactionException("Both source and destination accounts are required for TRANSFER");
        }
        if (srcId.equals(dstId)) {
            throw new InvalidTransactionException("Cannot transfer to the same account");
        }

        // Step 4: Deterministic Lock Ordering to prevent deadlocks: always lock lower ID first
        String firstId = srcId.compareTo(dstId) < 0 ? srcId : dstId;
        String secondId = srcId.compareTo(dstId) < 0 ? dstId : srcId;

        Account firstAccount = accountDAO.findByIdForUpdate(conn, firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        Account secondAccount = accountDAO.findByIdForUpdate(conn, secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));

        Account source = firstAccount.getAccountId().equals(srcId) ? firstAccount : secondAccount;
        Account destination = secondAccount.getAccountId().equals(dstId) ? secondAccount : firstAccount;

        if (!source.isActive()) {
            throw new AccountInactiveException("Source account is not active: " + srcId);
        }
        if (!destination.isActive()) {
            throw new AccountInactiveException("Destination account is not active: " + dstId);
        }

        // Check source sufficiency
        if (!source.isSufficientFor(req.amount())) {
            throw new InsufficientBalanceException("Insufficient balance in source account " + srcId +
                    ". Available: " + source.getBalance() + ", Requested: " + req.amount());
        }

        // Step 5: Apply operation
        BigDecimal srcBefore = source.getBalance();
        source.debit(req.amount());
        BigDecimal srcAfter = source.getBalance();

        BigDecimal dstBefore = destination.getBalance();
        destination.credit(req.amount());
        BigDecimal dstAfter = destination.getBalance();

        accountDAO.updateBalance(conn, srcId, srcAfter);
        accountDAO.updateBalance(conn, dstId, dstAfter);

        long duration = System.currentTimeMillis() - startTime;

        // Record Transaction
        TransactionRecord record = new TransactionRecord(
                req.transactionId(), srcId, dstId, TransactionType.TRANSFER,
                req.amount(), TransactionStatus.SUCCESS, LocalDateTime.now()
        );
        transactionDAO.saveTransaction(conn, record);

        // Record Audit
        TransactionAudit audit = new TransactionAudit(
                null, req.transactionId(), srcBefore, srcAfter,
                dstBefore, dstAfter, Thread.currentThread().getName(), duration, LocalDateTime.now()
        );
        transactionDAO.saveAudit(conn, audit);

        return TransactionResult.success(req.transactionId(), "Transfer completed successfully", srcAfter, dstAfter, duration);
    }

    private void validateRequest(TransactionRequest req) {
        if (req.transactionId() == null || req.transactionId().isBlank()) {
            throw new InvalidTransactionException("Transaction ID cannot be null or empty");
        }
        if (req.type() == null) {
            throw new InvalidTransactionException("Transaction type cannot be null");
        }
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(ErrorCategory.INVALID_AMOUNT, "Transaction amount must be strictly greater than zero");
        }
    }

    private void recordRejectionOrFailure(TransactionRequest req, TransactionStatus status, ErrorCategory category, String reason) {
        try (Connection conn = connectionManager.getConnection()) {
            // Record in transaction table if not already present
            if (!transactionDAO.isDuplicate(conn, req.transactionId())) {
                TransactionRecord record = new TransactionRecord(
                        req.transactionId(), req.sourceAccount(), req.destinationAccount(),
                        req.type(), req.amount() != null ? req.amount() : BigDecimal.ZERO,
                        status, LocalDateTime.now()
                );
                transactionDAO.saveTransaction(conn, record);
            }

            // Record in failure diagnostics
            FailedTransaction failure = new FailedTransaction(
                    null, req.transactionId(), category, reason, LocalDateTime.now()
            );
            transactionDAO.saveFailure(conn, failure);

        } catch (Exception e) {
            logger.error("Failed to persist failure diagnostic for transaction [{}]: {}", req.transactionId(), e.getMessage());
        }
    }

    private void setupMdc(TransactionRequest req) {
        MDC.put("txId", req.transactionId());
        MDC.put("op", req.type() != null ? req.type().name() : "UNKNOWN");
        MDC.put("thread", Thread.currentThread().getName());
    }

    private boolean isDuplicateKeyViolation(SQLException ex) {
        String sqlState = ex.getSQLState();
        int errorCode = ex.getErrorCode();
        return (sqlState != null && (sqlState.startsWith("23") || "23505".equals(sqlState) || "23000".equals(sqlState)))
                || errorCode == 1062;
    }
}
