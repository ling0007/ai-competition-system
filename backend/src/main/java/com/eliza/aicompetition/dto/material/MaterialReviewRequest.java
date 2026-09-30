package com.eliza.aicompetition.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 材料审核请求 DTO。
 * <p>
 * <b>安全注意</b>：审核人身份由后端从 JWT 中获取，不从请求体传递，
 * 因此本 DTO 不包含 reviewerId 字段。防止前端伪造审核人身份。
 * </p>
 */
@Data
public class MaterialReviewRequest {
    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    @NotNull(message = "materialId 不能为空")
    private Long materialId;

    @NotBlank(message = "reviewStatus 不能为空，应为 approved 或 revision")
    private String reviewStatus;

    private String reviewComment;
}
