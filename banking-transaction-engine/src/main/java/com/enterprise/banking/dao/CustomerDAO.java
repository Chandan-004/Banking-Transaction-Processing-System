package com.enterprise.banking.dao;

import com.enterprise.banking.model.Customer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object interface for Customer records.
 */
public interface CustomerDAO {

    /**
     * Finds a customer by ID.
     */
    Optional<Customer> findById(Connection conn, String customerId) throws SQLException;

    /**
     * Persists a customer master record.
     */
    void save(Connection conn, Customer customer) throws SQLException;
}
