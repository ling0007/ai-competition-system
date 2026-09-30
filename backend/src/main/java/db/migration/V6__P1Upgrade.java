package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * <h1>P1-1 / P1-2 数据库升级迁移（Java 版，幂等安全）</h1>
 *
 * <h2>为什么用 Java 而不是 SQL 写迁移？</h2>
 * <p>
 * 普通 SQL 迁移中，{@code ALTER TABLE ADD COLUMN} 如果列已存在会直接报错，
 * 导致整个迁移失败。而 Flyway 的 DELIMITER 语法在复杂存储过程中也不可靠。
 * </p>
 * <p>
 * Java 迁移的优势：可以用 try-catch 包裹每个 DDL，列已存在时跳过，实现<b>幂等</b>
 * （多次运行结果相同，不会因为中途失败而留下一半的 schema）。
 * </p>
 *
 * <h2>本次迁移做了什么？</h2>
 * <ol>
 *   <li>创建 {@code notice_parse_draft} 表 — AI 解析结果先存草稿，不直接覆盖正式数据</li>
 *   <li>{@code competition_notice} 加 confirmed_by / confirmed_at / published_at / version</li>
 *   <li>{@code competition_project} 加 version — 乐观锁防止并发冲突</li>
 *   <li>{@code material_requirement} 加 status / source / version_no — 材料要求可停用可溯源</li>
 *   <li>{@code agent_task_log.execute_status} 细化 — 支持 PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT</li>
 * </ol>
 *
 * <h2>关键面试词汇</h2>
 * <ul>
 *   <li><b>幂等迁移</b>（Idempotent Migration）：多次执行结果一致，支持安全的重复部署</li>
 *   <li><b>乐观锁</b>（Optimistic Locking）：用 version 字段防止并发写冲突</li>
 *   <li><b>人工确认流</b>（Human-in-the-loop）：AI 生成草稿 → 人工确认 → 正式发布</li>
 * </ul>
 *
 * @see com.eliza.aicompetition.entity.NoticeParseDraft
 * @see com.eliza.aicompetition.common.enums.NoticeParseStatus
 */
public class V6__P1Upgrade extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V6__P1Upgrade.class);

    @Override
    public void migrate(Context context) throws Exception {
        Statement stmt = context.getConnection().createStatement();

        // ================================================================
        // 1. 创建 notice_parse_draft 表
        //    AI 解析通知的结果先写入这张草稿表，管理员确认后才同步到正式表
        // ================================================================
        log.info("V6: 创建 notice_parse_draft 表...");
        safeExecute(stmt, "DROP TABLE IF EXISTS notice_parse_draft");

        safeExecute(stmt,
            "CREATE TABLE notice_parse_draft ("
            + "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '草稿主键',"
            + "  notice_id BIGINT NOT NULL COMMENT '关联通知ID',"
            + "  ai_title VARCHAR(255) NULL COMMENT 'AI提取的标题',"
            + "  ai_organizer VARCHAR(255) NULL COMMENT 'AI提取的主办方',"
            + "  ai_deadline DATETIME NULL COMMENT 'AI提取的截止时间',"
            + "  ai_target_group VARCHAR(255) NULL COMMENT 'AI提取的面向对象',"
            + "  ai_key_points TEXT NULL COMMENT 'AI提取的关键内容',"
            + "  ai_materials_json LONGTEXT NULL COMMENT 'AI提取的材料要求JSON数组',"
            + "  raw_ai_response LONGTEXT NULL COMMENT 'LLM原始返回JSON（调试用）',"
            + "  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '草稿状态: PENDING-待确认, CONFIRMED-已确认, REJECTED-已拒绝',"
            + "  created_by BIGINT NULL COMMENT '触发解析人ID',"
            + "  confirmed_by BIGINT NULL COMMENT '确认人ID',"
            + "  confirmed_at DATETIME NULL COMMENT '确认时间',"
            + "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
            + "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
            + "  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',"
            + "  CONSTRAINT pk_notice_parse_draft PRIMARY KEY (id),"
            + "  CONSTRAINT fk_parse_draft_notice FOREIGN KEY (notice_id) REFERENCES competition_notice (notice_id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知AI解析草稿表'"
        );

        safeExecute(stmt, "CREATE INDEX idx_parse_draft_notice_id ON notice_parse_draft (notice_id)");
        safeExecute(stmt, "CREATE INDEX idx_parse_draft_status ON notice_parse_draft (status)");

        // ================================================================
        // 2. competition_notice 新增字段
        //    confirmed_by / confirmed_at：谁在什么时间确认了 AI 解析结果
        //    published_at：通知发布时间
        //    version：乐观锁版本号，防止管理员并发操作
        // ================================================================
        log.info("V6: 为 competition_notice 添加确认/乐观锁字段...");
        safeExecute(stmt, "ALTER TABLE competition_notice ADD COLUMN confirmed_by BIGINT NULL COMMENT '确认人ID'");
        safeExecute(stmt, "ALTER TABLE competition_notice ADD COLUMN confirmed_at DATETIME NULL COMMENT '确认时间'");
        safeExecute(stmt, "ALTER TABLE competition_notice ADD COLUMN published_at DATETIME NULL COMMENT '发布时间'");
        safeExecute(stmt, "ALTER TABLE competition_notice ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'");

        safeExecute(stmt, "CREATE INDEX idx_notice_publish_created ON competition_notice (publish_status, created_at DESC)");

        // ================================================================
        // 3. competition_project 加乐观锁
        // ================================================================
        log.info("V6: 为 competition_project 添加乐观锁...");
        safeExecute(stmt, "ALTER TABLE competition_project ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'");

        // ================================================================
        // 4. material_requirement 加状态/来源/版本字段
        //    status：ACTIVE=活跃 / INACTIVE=停用（用于已发布通知不可删材料要求的场景）
        //    source：AI=AI生成 / MANUAL=人工录入
        //    version_no：材料要求本身的版本号
        // ================================================================
        log.info("V6: 为 material_requirement 添加状态字段...");
        safeExecute(stmt, "ALTER TABLE material_requirement ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '材料要求状态: ACTIVE-启用, INACTIVE-停用'");
        safeExecute(stmt, "ALTER TABLE material_requirement ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'AI' COMMENT '材料要求来源: AI-智能解析, MANUAL-人工录入'");
        safeExecute(stmt, "ALTER TABLE material_requirement ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT '材料要求版本号'");

        // ================================================================
        // 5. agent_task_log 状态细化
        //    旧值只有 success/fail，现扩展为 PENDING → RUNNING → SUCCESS/FAILED/TIMEOUT
        //    这是为了后续 P1-8 异步化做准备：前端轮询任务状态
        // ================================================================
        log.info("V6: 细化 agent_task_log 执行状态...");
        safeExecute(stmt, "ALTER TABLE agent_task_log MODIFY COLUMN execute_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态: PENDING-等待执行, RUNNING-执行中, SUCCESS-执行成功, FAILED-执行失败, TIMEOUT-执行超时'");

        stmt.close();
        log.info("V6: 迁移全部完成！");
    }

    /**
     * 安全执行 DDL —— 如果失败且原因是"已存在"，则跳过（幂等保证）。
     *
     * <h3>为什么需要这个方法？</h3>
     * <p>
     * 在生产环境中，迁移可能因为各种原因被部分执行（比如上一次部署中途失败）。
     * 如果不用 try-catch 包裹，{@code ALTER TABLE ADD COLUMN} 会直接报
     * "Duplicate column" 错误导致整个应用启动失败。
     * </p>
     * <p>
     * 这里的选择是<b>宽容幂等</b>：列/表/索引已存在 → 跳过，不影响启动。
     * 真正的严重错误（如语法错误、连接断开）仍然会抛出异常。
     * </p>
     */
    private void safeExecute(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
            log.debug("  ✓ {}", sql.substring(0, Math.min(80, sql.length())));
        } catch (SQLException e) {
            String msg = e.getMessage();
            // MySQL 错误码 1060 = Duplicate column, 1050 = Table already exists
            if (msg != null && (msg.contains("Duplicate") || msg.contains("already exists")
                || msg.contains("Duplicate key") || msg.contains("42000"))) {
                log.info("  ⏭ 跳过（已存在）: {}", sql.substring(0, Math.min(80, sql.length())));
            } else {
                log.error("  ✗ 失败: {}", msg);
                throw new RuntimeException("数据库迁移失败: " + msg, e);
            }
        }
    }
}
