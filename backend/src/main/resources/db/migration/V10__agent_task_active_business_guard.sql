-- A business entity may have at most one unfinished AI task. NULL permits
-- any number of terminal attempts for the same entity.
ALTER TABLE agent_task_log
ADD COLUMN active_marker TINYINT NULL DEFAULT NULL
    COMMENT '未完成任务为1，终态为NULL；与业务键共同防并发重复执行';

UPDATE agent_task_log
SET active_marker = 1
WHERE execute_status IN ('PENDING', 'RUNNING')
  AND business_type IS NOT NULL AND business_id IS NOT NULL AND is_deleted = 0;

ALTER TABLE agent_task_log
ADD CONSTRAINT uk_agent_task_active_business
    UNIQUE (business_type, business_id, active_marker);
