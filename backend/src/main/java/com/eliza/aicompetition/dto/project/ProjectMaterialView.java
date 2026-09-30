package com.eliza.aicompetition.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMaterialView {
    private Long materialId;
    private Long requirementId;
    private String requirementName;
    private Integer requiredFlag;
    private String description;
    /** @deprecated 使用 currentVersionId 判断提交状态 */
    @Deprecated
    private String submitStatus;
    /** 当前有效版本ID（NULL=未提交） */
    private Long currentVersionId;
    /** 当前 requirement 是否存在已上传的 current version */
    private Boolean uploaded;
    /** 当前 requirement 是否满足项目完整性要求；选交项不阻塞完整度 */
    private Boolean requirementSatisfied;
    private String fileHash;
    private Long fileId;
    private String fileName;
    private Integer versionNo;
    private String remark;
    private LocalDateTime submittedAt;
    /** 最新审核决定，来自 material_review 表（APPROVED / REVISION_REQUIRED），无审核记录时为 null */
    private String reviewStatus;
    /** 最新审核意见，来自 material_review 表 */
    private String reviewComment;
    /** 最新审核人姓名（关联 material_review → sys_user） */
    private String reviewedByName;
    /** 最新审核时间（material_review.created_at） */
    private LocalDateTime reviewedAt;
}
