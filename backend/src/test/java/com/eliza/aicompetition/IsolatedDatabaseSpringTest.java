package com.eliza.aicompetition;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Refuse to start Spring/Flyway tests against the application's default development database. */
abstract class IsolatedDatabaseSpringTest {
    @DynamicPropertySource
    static void requireDedicatedDatabase(DynamicPropertyRegistry registry) {
        String schema = System.getenv("DB_NAME");
        if (schema == null || !schema.matches("ai_competition_(?:test|phase5_e2e)_[a-f0-9]{8}")) {
            throw new IllegalStateException("Spring database tests require DB_NAME=ai_competition_test_<8 hex> "
                + "or ai_competition_phase5_e2e_<8 hex>; refusing the default development database");
        }
        String host = envOr("DB_HOST", "localhost");
        String port = envOr("DB_PORT", "3306");
        String sslMode = envOr("DB_SSL_MODE", "REQUIRED");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + schema
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sslMode="
            + sslMode + "&allowPublicKeyRetrieval=true");
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
