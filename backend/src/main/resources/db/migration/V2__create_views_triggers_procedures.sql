DROP VIEW IF EXISTS v_project_material_detail;
DROP VIEW IF EXISTS v_notice_material_summary;
DROP VIEW IF EXISTS v_project_progress;

DROP TRIGGER IF EXISTS trg_project_material_after_insert_refresh_project;
DROP TRIGGER IF EXISTS trg_project_material_after_update_refresh_project;

DROP PROCEDURE IF EXISTS sp_project_material_summary;
DROP PROCEDURE IF EXISTS sp_refresh_project_progress;

DELIMITER $$

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
END$$

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
END$$

CREATE TRIGGER trg_project_material_after_insert_refresh_project
AFTER INSERT ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
END$$

CREATE TRIGGER trg_project_material_after_update_refresh_project
AFTER UPDATE ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
    IF OLD.project_id <> NEW.project_id THEN
        CALL sp_refresh_project_progress(OLD.project_id);
    END IF;
END$$

DELIMITER ;

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
