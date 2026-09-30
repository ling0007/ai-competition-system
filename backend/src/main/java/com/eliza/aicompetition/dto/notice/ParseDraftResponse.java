package com.eliza.aicompetition.dto.notice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 解析草稿响应 —— 返回解析草稿的完整内容，供前端展示和编辑。
 */
public record ParseDraftResponse(
    Long id,
    Long noticeId,
    String aiTitle,
    String aiOrganizer,
    LocalDateTime aiDeadline,
    String aiTargetGroup,
    String aiKeyPoints,
    List<MaterialItem> materials,
    String rawAiResponse,
    String status,
    LocalDateTime createdAt
) {
    /**
     * 草稿中的单条材料要求。
     */
    public record MaterialItem(
        String name,
        String description,
        boolean isRequired
    ) {}
}
