-- ============================================================
-- V3: 状态模型重构
-- 重构项目状态机、分离 AI 检查与教师审核、新增材料版本管理
-- ============================================================

-- ==================== 第一步：新增结构和字段 ====================

-- 1.1/1.2 通知确认字段、通知版本号和项目版本号已由 V6 创建。
-- 本版本只负责 V6 未覆盖的状态模型结构，避免重复 DDL。

-- 1.3 材料当前版本 ID（替代 submit_status）
ALTER TABLE project_material
ADD COLUMN current_version_id BIGINT NULL
    COMMENT '当前有效版本ID（指向同一material_id下最新已提交版本，NULL=未提交）'
    AFTER file_id;

ALTER TABLE project_material
ADD COLUMN file_hash VARCHAR(64) NULL
    COMMENT '文件SHA-256哈希，用于去重和版本比对'
    AFTER file_id;

-- 1.4 材料版本表索引
CREATE INDEX idx_project_material_current_version
ON project_material (current_version_id);

-- ==================== 第二步：创建新表 ====================

-- 2.1 通知解析草稿表已由 V6 创建。

-- 2.2 AI 检查记录表
CREATE TABLE IF NOT EXISTS project_ai_check (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI检查记录主键',
    project_id BIGINT NOT NULL COMMENT '所属项目ID',
    agent_task_id BIGINT NULL COMMENT '关联的Agent任务ID',
    material_snapshot VARCHAR(500) NULL COMMENT '检查时使用的材料版本ID集合（JSON数组或逗号分隔）',
    result VARCHAR(20) NOT NULL COMMENT 'AI检查结果：PASSED-通过, WARNING-有风险',
    issue_summary VARCHAR(500) NULL COMMENT '问题摘要',
    issue_detail_json MEDIUMTEXT NULL COMMENT '详细问题JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    PRIMARY KEY (id),
    INDEX idx_ai_check_project_id (project_id),
    INDEX idx_ai_check_result (result),
    INDEX idx_ai_check_agent_task (agent_task_id),
    CONSTRAINT fk_ai_check_project FOREIGN KEY (project_id) REFERENCES competition_project(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI材料检查记录表';

-- 2.3 材料审核记录表
CREATE TABLE IF NOT EXISTS material_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录主键',
    material_id BIGINT NOT NULL COMMENT '被审核的材料ID',
    material_version_id BIGINT NOT NULL COMMENT '被审核的材料版本ID（必须绑定版本）',
    reviewer_id BIGINT NOT NULL COMMENT '审核人ID',
    decision VARCHAR(20) NOT NULL COMMENT '审核决定：APPROVED-通过, REVISION_REQUIRED-需修改',
    comment VARCHAR(1000) NULL COMMENT '审核意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    PRIMARY KEY (id),
    INDEX idx_material_review_material (material_id),
    INDEX idx_material_review_version (material_version_id),
    INDEX idx_material_review_reviewer (reviewer_id),
    CONSTRAINT fk_material_review_material FOREIGN KEY (material_id) REFERENCES project_material(material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料审核记录表';

-- 2.4 Agent 任务日志增强字段
ALTER TABLE agent_task_log
ADD COLUMN business_type VARCHAR(32) NULL
    COMMENT '业务类型：NOTICE_PARSE-通知解析, MATERIAL_CHECK-材料检查'
    AFTER tool_name;

ALTER TABLE agent_task_log
ADD COLUMN business_id BIGINT NULL
    COMMENT '关联业务ID（如notice_id, project_id）'
    AFTER business_type;

ALTER TABLE agent_task_log
ADD COLUMN parent_task_id BIGINT NULL
    COMMENT '父任务ID：重试时指向原始任务'
    AFTER business_id;

ALTER TABLE agent_task_log
ADD COLUMN attempt_no INT NOT NULL DEFAULT 1
    COMMENT '尝试次数：首次=1，每次重试递增'
    AFTER parent_task_id;

ALTER TABLE agent_task_log
ADD COLUMN request_payload MEDIUMTEXT NULL
    COMMENT '请求负载（JSON）'
    AFTER input_summary;

ALTER TABLE agent_task_log
ADD COLUMN response_payload MEDIUMTEXT NULL
    COMMENT '响应负载（JSON）'
    AFTER result_summary;

ALTER TABLE agent_task_log
ADD COLUMN error_message TEXT NULL
    COMMENT '错误信息'
    AFTER response_payload;

ALTER TABLE agent_task_log
ADD COLUMN started_at DATETIME NULL
    COMMENT '开始执行时间'
    AFTER created_at;

ALTER TABLE agent_task_log
ADD COLUMN finished_at DATETIME NULL
    COMMENT '完成时间'
    AFTER started_at;

-- ==================== 第三步：数据迁移 ====================

-- 3.1 迁移通知解析状态
-- CONFIRM_PENDING → PARSED
UPDATE competition_notice
SET parse_status = 'PARSED'
WHERE parse_status = 'CONFIRM_PENDING';

-- PARSE_FAILED → FAILED
UPDATE competition_notice
SET parse_status = 'FAILED'
WHERE parse_status = 'PARSE_FAILED';

-- 3.2 迁移项目状态
-- 所有旧状态映射为新的纯生命周期状态
-- MATERIAL_INCOMPLETE / READY_FOR_AI_CHECK / AI_WARNING / AI_PASSED → DRAFT
UPDATE competition_project
SET status = 'DRAFT'
WHERE status IN ('incomplete', 'ready', 'ai_warning', 'ai_passed',
                 'MATERIAL_INCOMPLETE', 'READY_FOR_AI_CHECK', 'AI_WARNING', 'AI_PASSED');

-- 标准化其他状态名称
UPDATE competition_project
SET status = 'DRAFT'
WHERE status = 'draft';

UPDATE competition_project
SET status = 'UNDER_REVIEW'
WHERE status IN ('under_review', 'reviewed');

UPDATE competition_project
SET status = 'REVISION_REQUIRED'
WHERE status IN ('revision_required', 'REVISION_REQUIRED');

UPDATE competition_project
SET status = 'APPROVED'
WHERE status = 'approved';

-- 3.3 迁移材料当前版本
-- 为每个 project_material 设置 current_version_id = 同 requirement 下最新 submitted 版本的 material_id
UPDATE project_material pm
JOIN (
    SELECT pm2.project_id, pm2.requirement_id, MAX(pm2.material_id) AS latest_id
    FROM project_material pm2
    WHERE pm2.submit_status = 'submitted' AND pm2.file_id IS NOT NULL AND pm2.is_deleted = 0
    GROUP BY pm2.project_id, pm2.requirement_id
) latest ON pm.project_id = latest.project_id AND pm.requirement_id = latest.requirement_id
SET pm.current_version_id = latest.latest_id
WHERE pm.material_id = latest.latest_id;

-- 3.4 迁移 AI 检查结果到 project_ai_check
-- 从 review_record 中提取 AI 审核记录并迁移
INSERT INTO project_ai_check (project_id, result, issue_summary, created_at, is_deleted)
SELECT
    rr.project_id,
    CASE
        WHEN rr.review_result = 'pass' THEN 'PASSED'
        WHEN rr.review_result IN ('warning', 'reject') THEN 'WARNING'
        ELSE 'WARNING'
    END,
    LEFT(rr.review_comment, 500),
    rr.created_at,
    rr.is_deleted
FROM review_record rr
WHERE rr.review_type = 'ai' AND rr.is_deleted = 0;

-- 3.5 迁移教师审核结果到 material_review
-- 从 project_material 中提取审核信息并迁移
INSERT INTO material_review (material_id, material_version_id, reviewer_id, decision, comment, created_at, is_deleted)
SELECT
    pm.material_id,
    COALESCE(pm.current_version_id, pm.material_id),
    pm.reviewed_by,
    CASE
        WHEN pm.review_status = 'approved' THEN 'APPROVED'
        WHEN pm.review_status = 'revision' THEN 'REVISION_REQUIRED'
        ELSE 'APPROVED'
    END,
    pm.review_comment,
    COALESCE(pm.reviewed_at, pm.created_at),
    pm.is_deleted
FROM project_material pm
WHERE pm.review_status IS NOT NULL
  AND pm.reviewed_by IS NOT NULL
  AND pm.is_deleted = 0;

-- 3.6 补齐通知确认信息
-- 对于已经有 parse_status='PARSED' 且 publish_status='PUBLISHED' 的通知，
-- 用 created_by 作为 confirmed_by，updated_at 作为 confirmed_at
UPDATE competition_notice
SET confirmed_by = created_by,
    confirmed_at = COALESCE(published_at, updated_at)
WHERE parse_status = 'PARSED'
  AND publish_status IN ('PUBLISHED', 'ARCHIVED')
  AND confirmed_by IS NULL;

-- ==================== 第四步：更新存储过程和触发器 ====================

-- 4.1 删除旧触发器（不再需要自动修改项目状态）
DROP TRIGGER IF EXISTS trg_project_material_after_insert_refresh_project;
DROP TRIGGER IF EXISTS trg_project_material_after_update_refresh_project;

-- 4.2 更新存储过程：只计算完成率，不修改项目状态
DROP PROCEDURE IF EXISTS sp_refresh_project_progress;

DELIMITER //

CREATE PROCEDURE sp_refresh_project_progress(IN p_project_id BIGINT)
BEGIN
    DECLARE v_required_total INT DEFAULT 0;
    DECLARE v_submitted_total INT DEFAULT 0;
    DECLARE v_completion_rate DECIMAL(5,2) DEFAULT 0.00;

    -- 统计必交材料总数和已提交数（通过 current_version_id 判断）
    SELECT
        COUNT(*) AS required_total,
        SUM(CASE WHEN pm.current_version_id IS NOT NULL THEN 1 ELSE 0 END) AS submitted_total
    INTO v_required_total, v_submitted_total
    FROM project_material pm
    JOIN material_requirement mr ON pm.requirement_id = mr.requirement_id
    WHERE pm.project_id = p_project_id
      AND mr.is_required = 1
      AND pm.is_deleted = 0
      AND mr.is_deleted = 0;

    -- 计算完成率
    IF v_required_total > 0 THEN
        SET v_completion_rate = ROUND(v_submitted_total * 100.0 / v_required_total, 2);
    ELSE
        SET v_completion_rate = 0.00;
    END IF;

    -- 只更新完成率，不修改项目状态
    UPDATE competition_project
    SET completion_rate = v_completion_rate
    WHERE project_id = p_project_id;
END //

DELIMITER ;

-- 4.3 重建触发器（只触发进度刷新，不改状态）
CREATE TRIGGER trg_project_material_after_insert_refresh_project
AFTER INSERT ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
END;

CREATE TRIGGER trg_project_material_after_update_refresh_project
AFTER UPDATE ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
    IF OLD.project_id != NEW.project_id THEN
        CALL sp_refresh_project_progress(OLD.project_id);
    END IF;
END;

-- ==================== 第五步：更新视图 ====================

-- 5.1 更新项目进度视图
DROP VIEW IF EXISTS v_project_progress;
CREATE VIEW v_project_progress AS
SELECT
    cp.project_id,
    cp.project_name,
    cp.status AS project_status,
    cp.completion_rate,
    cp.deadline,
    cp.leader_id,
    su.real_name AS leader_name,
    cn.title AS notice_title,
    cn.publish_status AS notice_publish_status
FROM competition_project cp
JOIN sys_user su ON cp.leader_id = su.user_id
JOIN competition_notice cn ON cp.notice_id = cn.notice_id
WHERE cp.is_deleted = 0;

-- 5.2 更新材料详情视图（使用 current_version_id）
DROP VIEW IF EXISTS v_project_material_detail;
CREATE VIEW v_project_material_detail AS
SELECT
    pm.material_id,
    pm.project_id,
    pm.requirement_id,
    pm.current_version_id,
    pm.version_no,
    pm.submit_status,
    pm.review_status,
    pm.review_comment,
    pm.submitted_at,
    pm.reviewed_at,
    fa.file_name,
    fa.file_size,
    mr.requirement_name,
    mr.is_required
FROM project_material pm
LEFT JOIN file_asset fa ON pm.file_id = fa.file_id
JOIN material_requirement mr ON pm.requirement_id = mr.requirement_id
WHERE pm.is_deleted = 0;
