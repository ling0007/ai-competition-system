DROP VIEW IF EXISTS v_project_material_detail;
DROP VIEW IF EXISTS v_notice_material_summary;
DROP VIEW IF EXISTS v_project_progress;

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
