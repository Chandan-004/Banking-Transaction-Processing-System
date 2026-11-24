package com.enterprise.banking;

import com.enterprise.banking.config.EnvLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("EnvLoader Unit Tests")
class EnvLoaderTest {

    @Test
    @DisplayName("EnvLoader resolves keys from .env or returns specified fallback default")
    void envLoaderResolvesValuesOrFallback() {
        String dbUrl = EnvLoader.get("DB_URL");
        assertNotNull(dbUrl, "DB_URL should be loaded from .env or system environment");

        String mongoUri = EnvLoader.get("MONGO_URI");
        assertNotNull(mongoUri, "MONGO_URI should be loaded from .env");

        String nonExistent = EnvLoader.get("NON_EXISTENT_KEY_12345", "default_val");
        assertEquals("default_val", nonExistent);
    }
}
