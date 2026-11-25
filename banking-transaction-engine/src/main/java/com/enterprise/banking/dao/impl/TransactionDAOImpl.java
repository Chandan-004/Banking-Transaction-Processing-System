package com.enterprise.banking.dao.impl;

import com.enterprise.banking.dao.TransactionDAO;
import com.enterprise.banking.model.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Plain JDBC implementation of TransactionDAO.
 * Handles idempotency checks, transaction recording, balance audit trails, and failure logs.
 */
public class TransactionDAOImpl implements TransactionDAO {

    private static final String CHECK_DUPLICATE =
            "SELECT 1 FROM transaction WHERE transaction_id = ?";

    private static final String SELECT_BY_ID =
            "SELECT transaction_id, source_account_id, destination_account_id, transaction_type, amount, status, created_at " +
            "FROM transaction WHERE transaction_id = ?";

    private static final String INSERT_TRANSACTION =
            "INSERT INTO transaction (transaction_id, source_account_id, destination_account_id, transaction_type, amount, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_AUDIT =
            "INSERT INTO transaction_audit (transaction_id, source_balance_before, source_balance_after, " +
            "destination_balance_before, destination_balance_after, execution_thread, duration_ms, audited_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_FAILURE =
            "INSERT INTO failed_transaction (transaction_id, error_category, failure_reason, failed_at) " +
            "VALUES (?, ?, ?, ?)";

    @Override
    public boolean isDuplicate(Connection conn, String transactionId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(CHECK_DUPLICATE)) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<TransactionRecord> findById(Connection conn, String transactionId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TransactionType type = TransactionType.valueOf(rs.getString("transaction_type"));
                    TransactionStatus status = TransactionStatus.valueOf(rs.getString("status"));
                    Timestamp ts = rs.getTimestamp("created_at");

                    return Optional.of(new TransactionRecord(
                            rs.getString("transaction_id"),
                            rs.getString("source_account_id"),
                            rs.getString("destination_account_id"),
                            type,
                            rs.getBigDecimal("amount"),
                            status,
                            ts != null ? ts.toLocalDateTime() : null
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveTransaction(Connection conn, TransactionRecord record) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRANSACTION)) {
            stmt.setString(1, record.getTransactionId());
            stmt.setString(2, record.getSourceAccountId());
            stmt.setString(3, record.getDestinationAccountId());
            stmt.setString(4, record.getTransactionType().name());
            stmt.setBigDecimal(5, record.getAmount());
            stmt.setString(6, record.getStatus().name());

            LocalDateTime now = record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now();
            stmt.setTimestamp(7, Timestamp.valueOf(now));

            stmt.executeUpdate();
        }
    }

    @Override
    public void saveAudit(Connection conn, TransactionAudit audit) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_AUDIT)) {
            stmt.setString(1, audit.getTransactionId());
            stmt.setBigDecimal(2, audit.getSourceBalanceBefore());
            stmt.setBigDecimal(3, audit.getSourceBalanceAfter());
            stmt.setBigDecimal(4, audit.getDestinationBalanceBefore());
            stmt.setBigDecimal(5, audit.getDestinationBalanceAfter());
            stmt.setString(6, audit.getExecutionThread());
            stmt.setLong(7, audit.getDurationMs());

            LocalDateTime now = audit.getAuditedAt() != null ? audit.getAuditedAt() : LocalDateTime.now();
            stmt.setTimestamp(8, Timestamp.valueOf(now));

            stmt.executeUpdate();
        }
    }

    @Override
    public void saveFailure(Connection conn, FailedTransaction failure) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_FAILURE)) {
            stmt.setString(1, failure.getTransactionId());
            stmt.setString(2, failure.getErrorCategory().name());
            stmt.setString(3, failure.getFailureReason());

            LocalDateTime now = failure.getFailedAt() != null ? failure.getFailedAt() : LocalDateTime.now();
            stmt.setTimestamp(4, Timestamp.valueOf(now));

            stmt.executeUpdate();
        }
    }
}
