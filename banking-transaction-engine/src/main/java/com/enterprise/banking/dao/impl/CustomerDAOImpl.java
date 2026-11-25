package com.enterprise.banking.dao.impl;

import com.enterprise.banking.dao.CustomerDAO;
import com.enterprise.banking.model.Customer;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Plain JDBC implementation of CustomerDAO.
 * Uses parameterized PreparedStatements to prevent SQL injection.
 */
public class CustomerDAOImpl implements CustomerDAO {

    private static final String SELECT_BY_ID =
            "SELECT customer_id, full_name, email, phone_number, created_at FROM customer WHERE customer_id = ?";

    private static final String INSERT_CUSTOMER =
            "INSERT INTO customer (customer_id, full_name, email, phone_number, created_at) VALUES (?, ?, ?, ?, ?)";

    @Override
    public Optional<Customer> findById(Connection conn, String customerId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(Connection conn, Customer customer) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_CUSTOMER)) {
            stmt.setString(1, customer.getCustomerId());
            stmt.setString(2, customer.getFullName());
            stmt.setString(3, customer.getEmail());
            stmt.setString(4, customer.getPhoneNumber());

            LocalDateTime createdAt = customer.getCreatedAt() != null ? customer.getCreatedAt() : LocalDateTime.now();
            stmt.setTimestamp(5, Timestamp.valueOf(createdAt));

            stmt.executeUpdate();
        }
    }

    private Customer mapRowToCustomer(ResultSet rs) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("created_at");
        LocalDateTime createdAt = timestamp != null ? timestamp.toLocalDateTime() : null;

        return new Customer(
                rs.getString("customer_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone_number"),
                createdAt
        );
    }
}
