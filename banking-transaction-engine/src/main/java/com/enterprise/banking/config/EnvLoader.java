package com.enterprise.banking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight, pure Core Java utility to load environment variables from a .env file.
 * Prioritizes OS System Environment Variables > Java System Properties > .env values.
 */
public final class EnvLoader {

    private static final Logger logger = LoggerFactory.getLogger(EnvLoader.class);
    private static final Map<String, String> ENV_VARS = new HashMap<>();

    static {
        loadDotEnv();
    }

    private EnvLoader() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves a configuration property by key.
     *
     * @param key the environment variable name
     * @return the resolved value, or null if not found
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * Retrieves a configuration property with a fallback default.
     *
     * @param key          the environment variable name
     * @param defaultValue fallback value if key is not found
     * @return the resolved value or defaultValue
     */
    public static String get(String key, String defaultValue) {
        // 1. Highest priority: System OS environment
        String systemEnv = System.getenv(key);
        if (systemEnv != null && !systemEnv.isBlank()) {
            return systemEnv;
        }

        // 2. Second priority: Java System property (-Dkey=value)
        String systemProp = System.getProperty(key);
        if (systemProp != null && !systemProp.isBlank()) {
            return systemProp;
        }

        // 3. Third priority: .env file
        String envValue = ENV_VARS.get(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return defaultValue;
    }

    private static void loadDotEnv() {
        // Search in common local paths
        File[] candidateFiles = new File[]{
                new File(".env"),
                new File("banking-transaction-engine/.env"),
                new File("../.env")
        };

        File dotEnvFile = null;
        for (File candidate : candidateFiles) {
            if (candidate.exists() && candidate.isFile()) {
                dotEnvFile = candidate;
                break;
            }
        }

        if (dotEnvFile == null) {
            logger.info(".env file not detected. Using system environment and default properties.");
            return;
        }

        logger.info("Loading environment configuration from: {}", dotEnvFile.getAbsolutePath());
        try (BufferedReader reader = new BufferedReader(new FileReader(dotEnvFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines or comment lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int delimiterIndex = line.indexOf('=');
                if (delimiterIndex > 0) {
                    String key = line.substring(0, delimiterIndex).trim();
                    String value = line.substring(delimiterIndex + 1).trim();

                    // Strip optional quotes
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    ENV_VARS.put(key, value);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read .env file: {}", e.getMessage());
        }
    }
}
