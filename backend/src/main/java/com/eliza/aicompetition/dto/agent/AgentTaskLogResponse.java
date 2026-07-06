package com.eliza.aicompetition.dto.agent;

import java.time.LocalDateTime;

public record AgentTaskLogResponse(
    Long taskId,
    Long projectId,
    String toolName,
    String inputSummary,
    String resultSummary,
    String executeStatus,
    LocalDateTime createdAt
) {
}
