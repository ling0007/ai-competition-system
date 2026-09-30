ALTER TABLE agent_task_log
ADD COLUMN result_origin VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    COMMENT '结果来源：MODEL-真实模型结果, FALLBACK-降级结果, NONE-无可用结果, UNKNOWN-历史未分类记录'
    AFTER execute_status;
