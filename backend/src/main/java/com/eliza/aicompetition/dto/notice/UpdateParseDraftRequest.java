package com.eliza.aicompetition.dto.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 修改解析草稿请求 —— 管理员手动编辑 AI 解析结果。
 */
public record UpdateParseDraftRequest(
    @NotBlank(message = "标题不能为空")
    String aiTitle,

    String aiOrganizer,

    @NotNull(message = "截止日期不能为空")
    LocalDateTime aiDeadline,

    String aiTargetGroup,

    String aiKeyPoints,

    @NotNull(message = "材料列表不能为空")
    List<MaterialItem> materials
) {
    public record MaterialItem(
        @NotBlank(message = "材料名称不能为空")
        String name,
        String description,
        boolean isRequired
    ) {}
}
