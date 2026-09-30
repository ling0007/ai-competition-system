package com.eliza.aicompetition.dto.agent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目 AI 检查历史。stale 由当前材料版本集合与检查快照比较得出。
 */
public record ProjectAiCheckView(
    Long id,
    Long projectId,
    String result,
    String issueSummary,
    List<Long> materialVersionIds,
    boolean stale,
    LocalDateTime checkedAt
) {}
