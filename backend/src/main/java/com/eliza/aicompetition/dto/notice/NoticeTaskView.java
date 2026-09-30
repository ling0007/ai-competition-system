package com.eliza.aicompetition.dto.notice;

import java.time.LocalDateTime;

public record NoticeTaskView(
    Long taskId, String businessType, Long businessId, String status,
    String resultOrigin, Integer attemptNo, LocalDateTime createdAt,
    LocalDateTime startedAt, LocalDateTime finishedAt, String errorSummary
) {}
