package com.eliza.aicompetition.dto.notice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知详情读模型。将通知生命周期、附件和正式材料要求一次返回，避免前端依赖数据库实体。
 */
public record NoticeDetailResponse(
    Long noticeId,
    String title,
    String organizer,
    LocalDateTime deadline,
    String targetGroup,
    String rawText,
    String aiSummary,
    Long noticeFileId,
    String fileName,
    String parseStatus,
    String publishStatus,
    Long confirmedBy,
    LocalDateTime confirmedAt,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MaterialRequirementItem> materialRequirements
) {
    public record MaterialRequirementItem(
        Long requirementId,
        String name,
        String description,
        boolean required,
        Integer sortNo,
        String status
    ) {}
}
