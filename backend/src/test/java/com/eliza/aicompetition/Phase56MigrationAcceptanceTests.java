package com.eliza.aicompetition;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in V8 -> V12 acceptance on an explicitly named, empty test schema only. */
@EnabledIfSystemProperty(named = "phase56.migration.acceptance", matches = "true")
class Phase56MigrationAcceptanceTests {
    private static final String PRECHECK = "SELECT business_type,business_id,COUNT(*) FROM agent_task_log "
        + "WHERE execute_status IN ('PENDING','RUNNING') AND business_type IS NOT NULL "
        + "AND business_id IS NOT NULL AND is_deleted=0 "
        + "GROUP BY business_type,business_id HAVING COUNT(*)>1";

    @Test
    void precheckFindsDuplicatesThenResolvedHistoryMigratesThroughV12() throws Exception {
        String schema = System.getenv("PHASE56_MIGRATION_DB");
        assertNotNull(schema);
        assertTrue(schema.matches("ai_competition_test_[a-f0-9]{8}"));
        String url = "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
            + "/" + schema + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
            + "&sslMode=" + env("DB_SSL_MODE", "DISABLED") + "&allowPublicKeyRetrieval=true";
        String user = env("DB_USERNAME", "root");
        String password = env("DB_PASSWORD", "root");
        try (var connection = DriverManager.getConnection(url, user, password);
             var check = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables "
                 + "WHERE table_schema=?")) {
            check.setString(1, schema);
            try (var rows = check.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getInt(1), "Refusing to reuse a nonempty migration fixture");
            }
        }

        Flyway.configure().dataSource(url, user, password)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("8")).load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password);
             Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO agent_task_log(task_id,tool_name,business_type,business_id,"
                + "execute_status,created_at,started_at) VALUES "
                + "(995001,'parseNoticeTool','NOTICE_PARSE',995500,'PENDING','2024-01-02 03:04:05',NULL),"
                + "(995002,'parseNoticeTool','NOTICE_PARSE',995500,'RUNNING','2024-01-02 03:04:06',"
                + "'2024-01-02 03:04:07')");
            assertEquals(1, countRows(sql, PRECHECK), "Precheck must detect duplicate active tasks");
            assertEquals(2, countRows(sql, "SELECT task_id FROM agent_task_log "
                + "WHERE business_type='NOTICE_PARSE' AND business_id=995500"));
            // The fixture explicitly resolves one duplicate. V10 must not delete either history row.
            sql.executeUpdate("UPDATE agent_task_log SET execute_status='FAILED' WHERE task_id=995002");
            assertEquals(0, countRows(sql, PRECHECK));
            sql.executeUpdate("INSERT INTO sys_user(user_id,username,password,real_name,role) "
                + "VALUES (995101,'phase56-migration','{noop}pw','Migration','student')");
            sql.executeUpdate("INSERT INTO competition_notice(notice_id,title,created_by,parse_status,"
                + "publish_status) VALUES (995102,'Migration notice',995101,'PARSED','PUBLISHED')");
            sql.executeUpdate("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,"
                + "status) VALUES (995103,995102,995101,'Migration project','DRAFT')");
            sql.executeUpdate("INSERT INTO project_ai_check(project_id,material_snapshot,result) "
                + "VALUES (995103,'[123]','PASSED')");
        }

        Flyway.configure().dataSource(url, user, password)
            .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password);
             Statement sql = connection.createStatement()) {
            assertEquals(12, scalarInt(sql, "SELECT MAX(CAST(version AS UNSIGNED)) "
                + "FROM flyway_schema_history WHERE success=1"));
            assertEquals(2, countRows(sql, "SELECT task_id FROM agent_task_log "
                + "WHERE business_type='NOTICE_PARSE' AND business_id=995500"));
            assertEquals(1, scalarInt(sql, "SELECT active_marker FROM agent_task_log WHERE task_id=995001"));
            assertEquals(0, countRows(sql, "SELECT task_id FROM agent_task_log "
                + "WHERE task_id=995002 AND active_marker IS NOT NULL"));
            assertEquals("UNKNOWN", scalarString(sql,
                "SELECT result_origin FROM agent_task_log WHERE task_id=995001"));
            assertEquals("2024-01-02 03:04:05", scalarString(sql,
                "SELECT created_at FROM agent_task_log WHERE task_id=995001"));
            assertEquals(3, scalarInt(sql, "SELECT DATETIME_PRECISION FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='agent_task_log' AND column_name='created_at'"));
            assertEquals("text", scalarString(sql, "SELECT DATA_TYPE FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='project_ai_check' "
                + "AND column_name='material_snapshot'"));
            assertEquals("[123]", scalarString(sql,
                "SELECT material_snapshot FROM project_ai_check WHERE project_id=995103"));
            // Leave the migrated schema safe for application-startup acceptance.
            sql.executeUpdate("UPDATE agent_task_log SET execute_status='FAILED',active_marker=NULL "
                + "WHERE task_id=995001");
        }
        System.out.println("phase56_migration schema=" + schema
            + " precheckDuplicateGroups=1 retainedTaskRows=2 flywayVersion=12 legacySnapshot=[123]");
    }

    private static int countRows(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            int count = 0;
            while (rows.next()) count++;
            return count;
        }
    }

    private static int scalarInt(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String scalarString(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
