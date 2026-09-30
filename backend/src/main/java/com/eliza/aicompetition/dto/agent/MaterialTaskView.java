package com.eliza.aicompetition.dto.agent;

import java.time.LocalDateTime;

public record MaterialTaskView(Long taskId, Long projectId, String status, String resultOrigin,
    Integer attempt, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt,
    String errorSummary) {}
