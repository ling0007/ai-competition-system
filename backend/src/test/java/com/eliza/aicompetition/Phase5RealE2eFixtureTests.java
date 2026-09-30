package com.eliza.aicompetition;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in, isolated MySQL fixture for the real-browser Phase 5 acceptance run. */
@EnabledIfSystemProperty(named = "phase5.e2e.setup", matches = "true")
class Phase5RealE2eFixtureTests {

    @Test
    void provisionFreshDatabaseWithThreeRolesAndPublishableNotice() throws Exception {
        String schema = requiredEnv("PHASE5_E2E_DB_NAME");
        if (!schema.matches("ai_competition_phase5_e2e_[a-f0-9]{8}")) {
            throw new IllegalArgumentException("Phase 5 test schema name must use the dedicated prefix and 8 hex digits");
        }
        String fixturePassword = requiredEnv("PHASE5_E2E_PASSWORD");
        if (fixturePassword.length() < 8) throw new IllegalArgumentException("Test password must be at least 8 characters");

        String host = envOr("DB_HOST", "localhost");
        String port = envOr("DB_PORT", "3306");
        String dbUser = envOr("DB_USERNAME", "root");
        String dbPassword = envOr("DB_PASSWORD", "root");
        String params = "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sslMode="
            + envOr("DB_SSL_MODE", "REQUIRED") + "&allowPublicKeyRetrieval=true";
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/" + params;
        String schemaUrl = "jdbc:mysql://" + host + ":" + port + "/" + schema + params;

        // The fixed prefix and strict pattern above keep this DDL away from any existing application schema.
        try (Connection server = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
             PreparedStatement exists = server.prepareStatement(
                 "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?")) {
            exists.setString(1, schema);
            try (ResultSet rows = exists.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getInt(1), "Refusing to reuse an existing schema");
            }
            try (Statement ddl = server.createStatement()) {
                ddl.executeUpdate("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        }

        Flyway.configure().dataSource(schemaUrl, dbUser, dbPassword)
            .locations("classpath:db/migration").load().migrate();

        try (Connection db = DriverManager.getConnection(schemaUrl, dbUser, dbPassword)) {
            String hash = new BCryptPasswordEncoder().encode(fixturePassword);
            try (PreparedStatement update = db.prepareStatement(
                    "UPDATE sys_user SET password = ? WHERE username IN ('admin', 'teacher1', 'student1', 'student2')")) {
                update.setString(1, hash);
                assertEquals(4, update.executeUpdate());
            }
            long adminId = userId(db, "admin");
            long noticeId;
            try (PreparedStatement insert = db.prepareStatement(
                    "INSERT INTO competition_notice "
                        + "(title, organizer, deadline, target_group, raw_text, ai_summary, created_by, "
                        + "notice_type, parse_status, publish_status, confirmed_by, confirmed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPETITION', 'PARSED', 'DRAFT', ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "Phase5 真实接口隔离验收通知");
                insert.setString(2, "隔离测试组");
                insert.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
                insert.setString(4, "隔离测试账号");
                insert.setString(5, "按要求提交申报书和指导教师意见表。");
                insert.setString(6, "隔离测试夹具：解析结果已人工确认，待管理员通过真实接口发布。");
                insert.setLong(7, adminId);
                insert.setLong(8, adminId);
                assertEquals(1, insert.executeUpdate());
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    noticeId = keys.getLong(1);
                }
            }
            try (PreparedStatement insert = db.prepareStatement(
                    "INSERT INTO material_requirement "
                        + "(notice_id, requirement_name, is_required, description, sort_no) VALUES (?, ?, 1, ?, ?)")) {
                requirement(insert, noticeId, "项目申报书", "项目方案 PDF", 1);
                requirement(insert, noticeId, "指导教师意见表", "教师签字 PDF", 2);
            }
            System.out.println("Phase 5 isolated schema ready: " + schema + ", noticeId=" + noticeId);
        }
    }

    private static void requirement(PreparedStatement insert, long noticeId, String name, String description, int sortNo)
        throws Exception {
        insert.setLong(1, noticeId);
        insert.setString(2, name);
        insert.setString(3, description);
        insert.setInt(4, sortNo);
        assertEquals(1, insert.executeUpdate());
    }

    private static long userId(Connection db, String username) throws Exception {
        try (PreparedStatement query = db.prepareStatement("SELECT user_id FROM sys_user WHERE username = ?")) {
            query.setString(1, username);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be set");
        return value;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
