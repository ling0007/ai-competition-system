package com.eliza.aicompetition.dto.material;

import java.time.LocalDateTime;

public record MaterialReviewResponse(
    Long materialId,
    String reviewStatus,
    String reviewComment,
    LocalDateTime reviewedAt
) {
}
