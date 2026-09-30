-- Queue wait is measured from durable acceptance to the real worker claim.
-- Preserve existing values; new task timestamps use millisecond precision.
ALTER TABLE agent_task_log
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务接受时间，毫秒精度',
    MODIFY COLUMN started_at DATETIME(3) NULL COMMENT '工作线程成功领取时间，毫秒精度',
    MODIFY COLUMN finished_at DATETIME(3) NULL COMMENT '任务进入终态时间，毫秒精度';
