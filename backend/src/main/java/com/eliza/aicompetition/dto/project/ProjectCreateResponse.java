package com.eliza.aicompetition.dto.project;

import java.math.BigDecimal;

public record ProjectCreateResponse(
    Long projectId,
    String projectName,
    String status,
    BigDecimal completionRate,
    Integer initializedMaterialCount
) {
}
