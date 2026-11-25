package com.enterprise.banking.dao.impl;

import com.enterprise.banking.dao.AccountDAO;
import com.enterprise.banking.model.Account;
import com.enterprise.banking.model.AccountStatus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Plain JDBC implementation of AccountDAO.
 * Implements pessimistic row-level locking (SELECT ... FOR UPDATE) to ensure concurrency safety.
 */
public class AccountDAOImpl implements AccountDAO {

    private static final String SELECT_BY_ID =
            "SELECT account_id, customer_id, balance, currency, status, version, created_at, updated_at " +
            "FROM account WHERE account_id = ?";

    // Pessimistic Locking query - locks the account row at database level until commit/rollback
    private static final String SELECT_BY_ID_FOR_UPDATE =
            "SELECT account_id, customer_id, balance, currency, status, version, created_at, updated_at " +
            "FROM account WHERE account_id = ? FOR UPDATE";

    private static final String UPDATE_BALANCE =
            "UPDATE account SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE account_id = ?";

    private static final String INSERT_ACCOUNT =
            "INSERT INTO account (account_id, customer_id, balance, currency, status, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Optional<Account> findById(Connection conn, String accountId) throws SQLException {
        return executeQuery(conn, SELECT_BY_ID, accountId);
    }

    @Override
    public Optional<Account> findByIdForUpdate(Connection conn, String accountId) throws SQLException {
        return executeQuery(conn, SELECT_BY_ID_FOR_UPDATE, accountId);
    }

    @Override
    public void updateBalance(Connection conn, String accountId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_BALANCE)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setString(2, accountId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to update balance. Account not found: " + accountId);
            }
        }
    }

    @Override
    public void save(Connection conn, Account account) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_ACCOUNT)) {
            stmt.setString(1, account.getAccountId());
            stmt.setString(2, account.getCustomerId());
            stmt.setBigDecimal(3, account.getBalance());
            stmt.setString(4, account.getCurrency());
            stmt.setString(5, account.getStatus().name());
            stmt.setLong(6, account.getVersion());

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime createdAt = account.getCreatedAt() != null ? account.getCreatedAt() : now;
            LocalDateTime updatedAt = account.getUpdatedAt() != null ? account.getUpdatedAt() : now;

            stmt.setTimestamp(7, Timestamp.valueOf(createdAt));
            stmt.setTimestamp(8, Timestamp.valueOf(updatedAt));

            stmt.executeUpdate();
        }
    }

    private Optional<Account> executeQuery(Connection conn, String sql, String accountId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAccount(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        AccountStatus status;
        try {
            status = AccountStatus.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException e) {
            status = AccountStatus.ACTIVE;
        }

        return new Account(
                rs.getString("account_id"),
                rs.getString("customer_id"),
                rs.getBigDecimal("balance"),
                rs.getString("currency"),
                status,
                rs.getLong("version"),
                createdTs != null ? createdTs.toLocalDateTime() : null,
                updatedTs != null ? updatedTs.toLocalDateTime() : null
        );
    }
}
