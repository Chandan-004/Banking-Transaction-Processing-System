package com.enterprise.banking.dao;

import com.enterprise.banking.model.Account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object interface for Account entities.
 * Methods accept a java.sql.Connection to allow transaction boundaries
 * (commit/rollback) to be managed by the calling service.
 */
public interface AccountDAO {

    /**
     * Finds an account by ID without row locks.
     */
    Optional<Account> findById(Connection conn, String accountId) throws SQLException;

    /**
     * Finds an account by ID with a pessimistic row-level lock (SELECT ... FOR UPDATE).
     */
    Optional<Account> findByIdForUpdate(Connection conn, String accountId) throws SQLException;

    /**
     * Updates an account's balance in the database.
     */
    void updateBalance(Connection conn, String accountId, BigDecimal newBalance) throws SQLException;

    /**
     * Persists a new account row.
     */
    void save(Connection conn, Account account) throws SQLException;
}
