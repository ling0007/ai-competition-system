-- Optional diagnostic data for new attempts only. Historical rows stay NULL/UNKNOWN.
-- Business status/result_origin remain authoritative and are committed separately.
ALTER TABLE agent_task_log
    ADD COLUMN observation_payload JSON NULL COMMENT '安全的执行元数据；不含原文、Prompt、图片或密钥' AFTER response_payload,
    ADD COLUMN error_category VARCHAR(40) NULL COMMENT '稳定失败类别；旧任务为NULL' AFTER error_message,
    ADD COLUMN degradation_reason VARCHAR(40) NULL COMMENT '成功但降级或输入覆盖不完整的原因；旧任务为NULL' AFTER error_category;
