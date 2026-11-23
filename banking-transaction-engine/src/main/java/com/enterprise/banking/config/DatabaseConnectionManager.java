package com.enterprise.banking.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton database connection manager using HikariCP.
 * Thread-safe and shared across all worker threads.
 */
public class DatabaseConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private static volatile DatabaseConnectionManager instance;
    private final HikariDataSource dataSource;

    private DatabaseConnectionManager() {
        Properties props = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url", "jdbc:mysql://localhost:3306/banking_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
        config.setUsername(props.getProperty("db.username", "root"));
        config.setPassword(props.getProperty("db.password", "root"));

        int maxPoolSize = Integer.parseInt(props.getProperty("db.pool.maximum-size", "25"));
        int minIdle = Integer.parseInt(props.getProperty("db.pool.minimum-idle", "5"));
        long connTimeout = Long.parseLong(props.getProperty("db.pool.connection-timeout", "30000"));

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connTimeout);
        config.setPoolName("BankingTransactionEnginePool");

        // Prevents failure on initial instantiation if DB is starting up
        config.setInitializationFailTimeout(-1);

        // Sensible defaults for high-concurrency MySQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        logger.info("Initialized HikariCP connection pool with max-pool-size={}", maxPoolSize);
    }

    public static DatabaseConnectionManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnectionManager.class) {
                if (instance == null) {
                    instance = new DatabaseConnectionManager();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP connection pool closed.");
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("database.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                logger.warn("database.properties not found on classpath, using defaults");
            }
        } catch (Exception e) {
            logger.error("Failed to load database.properties, falling back to defaults", e);
        }
        return props;
    }
}
