package com.enterprise.banking.dao;

import com.enterprise.banking.model.FailedTransaction;
import com.enterprise.banking.model.TransactionAudit;
import com.enterprise.banking.model.TransactionRecord;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object interface for transactions, audits, and failure logging.
 */
public interface TransactionDAO {

    /**
     * Checks if a transaction ID already exists in the database.
     */
    boolean isDuplicate(Connection conn, String transactionId) throws SQLException;

    /**
     * Retrieves an existing transaction record by ID.
     */
    Optional<TransactionRecord> findById(Connection conn, String transactionId) throws SQLException;

    /**
     * Persists a transaction record into the transaction table.
     */
    void saveTransaction(Connection conn, TransactionRecord record) throws SQLException;

    /**
     * Persists financial before/after audit details into transaction_audit.
     */
    void saveAudit(Connection conn, TransactionAudit audit) throws SQLException;

    /**
     * Persists failure reason into failed_transaction.
     */
    void saveFailure(Connection conn, FailedTransaction failure) throws SQLException;
}
