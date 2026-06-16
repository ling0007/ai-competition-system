-- ============================================================
-- AI Competition System — Complete Schema Initialization
-- Single migration (V1) — complete schema initialization
-- ============================================================

-- ============================================================
-- Part 1: Tables
-- ============================================================

CREATE TABLE sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名',
    password VARCHAR(100) NOT NULL COMMENT '登录密码或加密摘要',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL COMMENT '角色: student/teacher/admin',
    phone VARCHAR(20) NULL COMMENT '手机号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_sys_user PRIMARY KEY (user_id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统用户表';

CREATE TABLE file_asset (
    file_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件主键',
    biz_type VARCHAR(30) NOT NULL COMMENT '业务类型: notice/material/other',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
    file_ext VARCHAR(20) NULL COMMENT '文件扩展名',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    storage_path VARCHAR(500) NULL COMMENT 'MinIO 或对象存储路径',
    file_blob LONGBLOB NULL COMMENT '课程设计演示备用二进制内容',
    uploaded_by BIGINT NULL COMMENT '上传人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_file_asset PRIMARY KEY (file_id),
    CONSTRAINT fk_file_asset_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '文件资源表';

CREATE TABLE competition_notice (
    notice_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '竞赛通知主键',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    organizer VARCHAR(200) NULL COMMENT '主办单位',
    deadline DATETIME NULL COMMENT '申报截止时间',
    target_group VARCHAR(255) NULL COMMENT '面向对象',
    raw_text LONGTEXT NULL COMMENT '通知原始文本',
    ai_summary TEXT NULL COMMENT 'AI解析摘要',
    notice_file_id BIGINT NULL COMMENT '通知文件ID',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_competition_notice PRIMARY KEY (notice_id),
    CONSTRAINT fk_notice_file_asset FOREIGN KEY (notice_file_id) REFERENCES file_asset (file_id),
    CONSTRAINT fk_notice_created_by FOREIGN KEY (created_by) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '竞赛通知表';

CREATE TABLE competition_project (
    project_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目主键',
    notice_id BIGINT NOT NULL COMMENT '所属竞赛通知ID',
    leader_id BIGINT NOT NULL COMMENT '项目负责人ID',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    team_name VARCHAR(100) NULL COMMENT '团队名称',
    status VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '状态: draft/checking/incomplete/ready/reviewed',
    deadline DATETIME NULL COMMENT '项目申报截止时间',
    completion_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '材料完成率(%)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_competition_project PRIMARY KEY (project_id),
    CONSTRAINT fk_project_notice FOREIGN KEY (notice_id) REFERENCES competition_notice (notice_id),
    CONSTRAINT fk_project_leader FOREIGN KEY (leader_id) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '竞赛申报项目表';

CREATE TABLE project_member (
    member_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目成员主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '成员用户ID',
    member_role VARCHAR(30) NOT NULL DEFAULT 'member' COMMENT '成员角色: leader/member/advisor',
    join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_project_member PRIMARY KEY (member_id),
    CONSTRAINT uk_project_member_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES competition_project (project_id),
    CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目成员表';

CREATE TABLE material_requirement (
    requirement_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料要求主键',
    notice_id BIGINT NOT NULL COMMENT '竞赛通知ID',
    requirement_name VARCHAR(100) NOT NULL COMMENT '材料名称',
    is_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否必交: 0-否, 1-是',
    description VARCHAR(500) NULL COMMENT '材料说明',
    sort_no INT NOT NULL DEFAULT 1 COMMENT '排序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_material_requirement PRIMARY KEY (requirement_id),
    CONSTRAINT fk_requirement_notice FOREIGN KEY (notice_id) REFERENCES competition_notice (notice_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '材料要求表';

CREATE TABLE project_material (
    material_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目材料主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    requirement_id BIGINT NOT NULL COMMENT '材料要求ID',
    file_id BIGINT NULL COMMENT '已上传文件ID',
    submit_status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '提交状态: pending/submitted/rejected',
    version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
    remark VARCHAR(500) NULL COMMENT '备注',
    review_status VARCHAR(20) DEFAULT NULL COMMENT '教师审核状态: NULL-未审核, approved-通过, revision-需修改',
    review_comment TEXT DEFAULT NULL COMMENT '教师审核意见/修改建议',
    reviewed_by BIGINT DEFAULT NULL COMMENT '审核教师ID',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    submitted_at DATETIME NULL COMMENT '提交时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_project_material PRIMARY KEY (material_id),
    CONSTRAINT uk_project_material_project_requirement_version UNIQUE (project_id, requirement_id, version_no),
    CONSTRAINT fk_project_material_project FOREIGN KEY (project_id) REFERENCES competition_project (project_id),
    CONSTRAINT fk_project_material_requirement FOREIGN KEY (requirement_id) REFERENCES material_requirement (requirement_id),
    CONSTRAINT fk_project_material_file FOREIGN KEY (file_id) REFERENCES file_asset (file_id),
    CONSTRAINT fk_project_material_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目已提交材料表';

CREATE TABLE review_record (
    review_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    reviewer_id BIGINT NULL COMMENT '审核人ID, AI检查时可为空',
    review_type VARCHAR(30) NOT NULL COMMENT '审核类型: ai/teacher',
    review_result VARCHAR(30) NOT NULL COMMENT '审核结果: pass/reject/warning',
    review_comment TEXT NULL COMMENT '审核意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_review_record PRIMARY KEY (review_id),
    CONSTRAINT fk_review_project FOREIGN KEY (project_id) REFERENCES competition_project (project_id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '检查与审核记录表';

CREATE TABLE notify_message (
    msg_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    receiver_id BIGINT NOT NULL COMMENT '接收人ID',
    msg_type VARCHAR(30) NOT NULL COMMENT '消息类型: deadline/material/system',
    msg_content VARCHAR(500) NOT NULL COMMENT '消息内容',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_notify_message PRIMARY KEY (msg_id),
    CONSTRAINT fk_notify_project FOREIGN KEY (project_id) REFERENCES competition_project (project_id),
    CONSTRAINT fk_notify_receiver FOREIGN KEY (receiver_id) REFERENCES sys_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '提醒消息表';

CREATE TABLE agent_task_log (
    task_id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent任务主键',
    project_id BIGINT NULL COMMENT '关联项目ID',
    tool_name VARCHAR(100) NOT NULL COMMENT '工具名称',
    input_summary TEXT NULL COMMENT '输入摘要',
    result_summary TEXT NULL COMMENT '结果摘要',
    execute_status VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '执行状态: success/fail',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    CONSTRAINT pk_agent_task_log PRIMARY KEY (task_id),
    CONSTRAINT fk_agent_task_project FOREIGN KEY (project_id) REFERENCES competition_project (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent任务日志表';

-- ============================================================
-- Part 2: Indexes
-- ============================================================

CREATE INDEX idx_file_asset_uploaded_by ON file_asset (uploaded_by);
CREATE INDEX idx_file_asset_biz_type ON file_asset (biz_type);

CREATE INDEX idx_notice_title ON competition_notice (title);
CREATE INDEX idx_notice_deadline ON competition_notice (deadline);
CREATE INDEX idx_notice_created_by ON competition_notice (created_by);

CREATE INDEX idx_project_name ON competition_project (project_name);
CREATE INDEX idx_project_status ON competition_project (status);
CREATE INDEX idx_project_deadline ON competition_project (deadline);
CREATE INDEX idx_project_notice ON competition_project (notice_id);
CREATE INDEX idx_project_leader_status ON competition_project (leader_id, status);

CREATE INDEX idx_project_member_user ON project_member (user_id);

CREATE INDEX idx_requirement_notice_id ON material_requirement (notice_id);
CREATE INDEX idx_requirement_notice_sort ON material_requirement (notice_id, sort_no);

CREATE INDEX idx_project_material_project_id ON project_material (project_id);
CREATE INDEX idx_project_material_requirement_id ON project_material (requirement_id);
CREATE INDEX idx_project_material_project_requirement ON project_material (project_id, requirement_id);
CREATE INDEX idx_project_material_file_id ON project_material (file_id);
CREATE INDEX idx_project_material_review_status ON project_material (review_status);
CREATE INDEX idx_project_material_reviewed_by ON project_material (reviewed_by);

CREATE INDEX idx_review_project_type ON review_record (project_id, review_type);
CREATE INDEX idx_review_reviewer ON review_record (reviewer_id);

CREATE INDEX idx_notify_receiver_id ON notify_message (receiver_id);
CREATE INDEX idx_notify_is_read ON notify_message (is_read);
CREATE INDEX idx_notify_receiver_read ON notify_message (receiver_id, is_read);

CREATE INDEX idx_agent_task_project_id ON agent_task_log (project_id);
CREATE INDEX idx_agent_task_tool_name ON agent_task_log (tool_name);

-- ============================================================
-- Part 3: Stored Procedures
-- ============================================================

CREATE PROCEDURE sp_refresh_project_progress(IN p_project_id BIGINT)
BEGIN
    DECLARE v_required_total INT DEFAULT 0;
    DECLARE v_required_submitted INT DEFAULT 0;
    DECLARE v_completion_rate DECIMAL(5, 2) DEFAULT 0.00;
    DECLARE v_project_status VARCHAR(30) DEFAULT 'draft';

    SELECT COUNT(*)
      INTO v_required_total
      FROM competition_project cp
      JOIN material_requirement mr
        ON mr.notice_id = cp.notice_id
       AND mr.is_required = 1
       AND mr.is_deleted = 0
     WHERE cp.project_id = p_project_id
       AND cp.is_deleted = 0;

    SELECT COUNT(*)
      INTO v_required_submitted
      FROM competition_project cp
      JOIN material_requirement mr
        ON mr.notice_id = cp.notice_id
       AND mr.is_required = 1
       AND mr.is_deleted = 0
      LEFT JOIN (
            SELECT pm.project_id, pm.requirement_id, pm.submit_status
              FROM project_material pm
              JOIN (
                    SELECT project_id, requirement_id, MAX(version_no) AS max_version_no
                      FROM project_material
                     WHERE is_deleted = 0
                     GROUP BY project_id, requirement_id
                   ) latest_pm
                ON latest_pm.project_id = pm.project_id
               AND latest_pm.requirement_id = pm.requirement_id
               AND latest_pm.max_version_no = pm.version_no
             WHERE pm.is_deleted = 0
      ) latest_material
        ON latest_material.project_id = cp.project_id
       AND latest_material.requirement_id = mr.requirement_id
     WHERE cp.project_id = p_project_id
       AND cp.is_deleted = 0
       AND latest_material.submit_status = 'submitted';

    IF v_required_total > 0 THEN
        SET v_completion_rate = ROUND(v_required_submitted * 100.00 / v_required_total, 2);
    ELSE
        SET v_completion_rate = 0.00;
    END IF;

    IF v_completion_rate >= 100.00 AND v_required_total > 0 THEN
        SET v_project_status = 'ready';
    ELSEIF v_completion_rate > 0.00 THEN
        SET v_project_status = 'incomplete';
    ELSE
        SET v_project_status = 'draft';
    END IF;

    UPDATE competition_project
       SET completion_rate = v_completion_rate,
           status = v_project_status
     WHERE project_id = p_project_id
       AND is_deleted = 0;
END;

CREATE PROCEDURE sp_project_material_summary(IN p_project_id BIGINT)
BEGIN
    SELECT
        cp.project_id,
        cp.project_name,
        COALESCE(summary.required_total, 0) AS required_total,
        COALESCE(summary.required_submitted, 0) AS required_submitted,
        COALESCE(summary.required_missing, 0) AS required_missing,
        COALESCE(summary.completion_rate, 0.00) AS completion_rate
    FROM competition_project cp
    LEFT JOIN (
        SELECT
            cp_inner.project_id,
            COUNT(mr.requirement_id) AS required_total,
            COUNT(CASE WHEN latest_material.submit_status = 'submitted' THEN 1 END) AS required_submitted,
            COUNT(mr.requirement_id) - COUNT(CASE WHEN latest_material.submit_status = 'submitted' THEN 1 END) AS required_missing,
            CASE
                WHEN COUNT(mr.requirement_id) = 0 THEN 0.00
                ELSE ROUND(COUNT(CASE WHEN latest_material.submit_status = 'submitted' THEN 1 END) * 100.00 / COUNT(mr.requirement_id), 2)
            END AS completion_rate
        FROM competition_project cp_inner
        LEFT JOIN material_requirement mr
          ON mr.notice_id = cp_inner.notice_id
         AND mr.is_required = 1
         AND mr.is_deleted = 0
        LEFT JOIN (
            SELECT pm.project_id, pm.requirement_id, pm.submit_status
              FROM project_material pm
              JOIN (
                    SELECT project_id, requirement_id, MAX(version_no) AS max_version_no
                      FROM project_material
                     WHERE is_deleted = 0
                     GROUP BY project_id, requirement_id
                   ) latest_pm
                ON latest_pm.project_id = pm.project_id
               AND latest_pm.requirement_id = pm.requirement_id
               AND latest_pm.max_version_no = pm.version_no
             WHERE pm.is_deleted = 0
        ) latest_material
            ON latest_material.project_id = cp_inner.project_id
           AND latest_material.requirement_id = mr.requirement_id
        WHERE cp_inner.project_id = p_project_id
          AND cp_inner.is_deleted = 0
        GROUP BY cp_inner.project_id
    ) summary
      ON summary.project_id = cp.project_id
   WHERE cp.project_id = p_project_id
     AND cp.is_deleted = 0;
END;

-- ============================================================
-- Part 4: Triggers
-- ============================================================

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
    IF OLD.project_id <> NEW.project_id THEN
        CALL sp_refresh_project_progress(OLD.project_id);
    END IF;
END;

-- ============================================================
-- Part 5: Views
-- ============================================================

CREATE VIEW v_project_progress AS
SELECT
    cp.project_id,
    cp.project_name,
    cp.team_name,
    cp.status,
    cp.deadline,
    cp.completion_rate,
    su.real_name AS leader_name,
    cn.title AS notice_title
FROM competition_project cp
JOIN sys_user su
  ON su.user_id = cp.leader_id
 AND su.is_deleted = 0
JOIN competition_notice cn
  ON cn.notice_id = cp.notice_id
 AND cn.is_deleted = 0
WHERE cp.is_deleted = 0;

CREATE VIEW v_notice_material_summary AS
SELECT
    cn.notice_id,
    cn.title,
    mr.requirement_id,
    mr.requirement_name,
    mr.is_required,
    mr.sort_no
FROM competition_notice cn
JOIN material_requirement mr
  ON mr.notice_id = cn.notice_id
 AND mr.is_deleted = 0
WHERE cn.is_deleted = 0;

CREATE VIEW v_project_material_detail AS
SELECT
    cp.project_id,
    cp.project_name,
    mr.requirement_id,
    mr.requirement_name,
    mr.is_required,
    COALESCE(latest_material.submit_status, 'pending') AS submit_status,
    fa.file_name,
    latest_material.version_no,
    latest_material.submitted_at
FROM competition_project cp
JOIN material_requirement mr
  ON mr.notice_id = cp.notice_id
 AND mr.is_deleted = 0
LEFT JOIN (
    SELECT pm.project_id, pm.requirement_id, pm.file_id, pm.submit_status, pm.version_no, pm.submitted_at
      FROM project_material pm
      JOIN (
            SELECT project_id, requirement_id, MAX(version_no) AS max_version_no
              FROM project_material
             WHERE is_deleted = 0
             GROUP BY project_id, requirement_id
      ) latest_pm
        ON latest_pm.project_id = pm.project_id
       AND latest_pm.requirement_id = pm.requirement_id
       AND latest_pm.max_version_no = pm.version_no
     WHERE pm.is_deleted = 0
) latest_material
  ON latest_material.project_id = cp.project_id
 AND latest_material.requirement_id = mr.requirement_id
LEFT JOIN file_asset fa
  ON fa.file_id = latest_material.file_id
 AND fa.is_deleted = 0
WHERE cp.is_deleted = 0;

-- ============================================================
-- Part 6: Demo Data
-- ============================================================

INSERT INTO sys_user (username, password, real_name, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '系统管理员', 'admin'),
('teacher1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '张老师', 'teacher'),
('student1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '李明', 'student'),
('student2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '王芳', 'student');
