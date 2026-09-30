USE ai_competition_db;

-- 1. 查询所有表是否创建成功
SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ai_competition_db'
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2. 查询所有索引是否创建成功
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ai_competition_db'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 3. 查询所有视图是否创建成功
SELECT TABLE_NAME, VIEW_DEFINITION
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = 'ai_competition_db'
ORDER BY TABLE_NAME;

-- 4. 验证 v_project_progress 是否可用
SELECT *
FROM v_project_progress;

-- 5. 验证 v_notice_material_summary 是否可用
SELECT *
FROM v_notice_material_summary
ORDER BY notice_id, sort_no;

-- 6. 验证 v_project_material_detail 是否可用
SELECT *
FROM v_project_material_detail
WHERE project_id = 1
ORDER BY requirement_id;

-- 7. 验证存储过程 sp_project_material_summary 是否可用
CALL sp_project_material_summary(1);

-- 8. 通过事务更新一条项目材料来验证触发器是否能自动刷新项目完成率与状态
START TRANSACTION;

UPDATE project_material
   SET file_id = 3,
       submit_status = 'submitted',
       submitted_at = CURRENT_TIMESTAMP,
       remark = '验证脚本：补交团队成员信息表'
 WHERE project_id = 1
   AND requirement_id = 2
   AND version_no = 1;

SELECT project_id, project_name, status, completion_rate
FROM competition_project
WHERE project_id = 1;

CALL sp_project_material_summary(1);

ROLLBACK;

-- 9. 查询 demo 项目的材料完成情况
SELECT project_id, project_name, status, completion_rate
FROM competition_project
WHERE project_id = 1;

SELECT *
FROM v_project_material_detail
WHERE project_id = 1
ORDER BY requirement_id;

-- 10. 查询某个用户收到的未读提醒消息
SELECT msg_id, project_id, receiver_id, msg_type, msg_content, created_at
FROM notify_message
WHERE receiver_id = 3
  AND is_read = 0
  AND is_deleted = 0
ORDER BY created_at DESC;

-- 11. 查询最近执行的 Agent 工具记录
SELECT task_id, project_id, tool_name, execute_status, created_at
FROM agent_task_log
WHERE is_deleted = 0
ORDER BY created_at DESC, task_id DESC
LIMIT 10;
