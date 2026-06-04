package com.eliza.aicompetition.dto.agent;

import java.math.BigDecimal;
import java.util.List;

public record MaterialCheckResponse(
    Long projectId,
    String projectName,
    String reviewResult,
    String reviewComment,
    BigDecimal completionRate,
    List<String> missingMaterials
) {
}
