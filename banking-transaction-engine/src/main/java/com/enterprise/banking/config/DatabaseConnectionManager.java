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
 * Seamlessly resolves configuration with priority:
 * 1. OS Environment Variables & .env file (via EnvLoader)
 * 2. database.properties file
 * 3. Sensible local defaults
 */
public class DatabaseConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private static volatile DatabaseConnectionManager instance;
    private final HikariDataSource dataSource;

    private DatabaseConnectionManager() {
        Properties props = loadProperties();

        // Resolve credentials from .env first, fallback to database.properties
        String jdbcUrl = EnvLoader.get("DB_URL", props.getProperty("db.url",
                "jdbc:mysql://localhost:3306/banking_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
        String username = EnvLoader.get("DB_USERNAME", props.getProperty("db.username", "root"));
        String password = EnvLoader.get("DB_PASSWORD", props.getProperty("db.password", "root"));

        int maxPoolSize = Integer.parseInt(EnvLoader.get("DB_POOL_MAX_SIZE",
                props.getProperty("db.pool.maximum-size", "25")));
        int minIdle = Integer.parseInt(EnvLoader.get("DB_POOL_MIN_IDLE",
                props.getProperty("db.pool.minimum-idle", "5")));
        long connTimeout = Long.parseLong(EnvLoader.get("DB_POOL_TIMEOUT",
                props.getProperty("db.pool.connection-timeout", "30000")));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

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
        logger.info("Initialized HikariCP connection pool [url={}, poolSize={}]", jdbcUrl, maxPoolSize);
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
