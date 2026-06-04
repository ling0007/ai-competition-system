package com.eliza.aicompetition.dto.material;

import java.math.BigDecimal;

public record MaterialUploadResponse(
    Long materialId,
    Long projectId,
    Long requirementId,
    Long fileId,
    Integer versionNo,
    String submitStatus,
    String projectStatus,
    BigDecimal completionRate
) {
}
