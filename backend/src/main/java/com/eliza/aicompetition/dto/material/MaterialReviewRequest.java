package com.eliza.aicompetition.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialReviewRequest {
    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    @NotNull(message = "materialId 不能为空")
    private Long materialId;

    @NotBlank(message = "reviewStatus 不能为空，应为 approved 或 revision")
    private String reviewStatus;

    private String reviewComment;

    @NotNull(message = "reviewerId 不能为空")
    private Long reviewerId;
}
