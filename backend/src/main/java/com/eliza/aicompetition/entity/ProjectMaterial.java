package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_material")
public class ProjectMaterial {
    @TableId(value = "material_id", type = IdType.AUTO)
    private Long materialId;
    private Long projectId;
    private Long requirementId;
    private Long fileId;

    /**
     * 文件SHA-256哈希，用于去重和版本比对。
     */
    private String fileHash;

    /**
     * 当前有效版本ID。
     * NULL = 未提交，非NULL = 已提交（指向同一requirement下最新已提交版本的material_id）。
     * 替代原来的 submit_status 字段。
     */
    private Long currentVersionId;

    /**
     * @deprecated 使用 currentVersionId 判断提交状态。此字段保留用于数据迁移兼容。
     */
    @Deprecated
    private String submitStatus;

    private Integer versionNo;
    private String remark;
    private LocalDateTime submittedAt;

    /**
     * @deprecated 审核结果使用 material_review 表。此字段保留用于数据迁移兼容。
     */
    @Deprecated
    private String reviewStatus;

    /**
     * @deprecated 审核意见使用 material_review 表。此字段保留用于数据迁移兼容。
     */
    @Deprecated
    private String reviewComment;

    /**
     * @deprecated 审核人使用 material_review 表。此字段保留用于数据迁移兼容。
     */
    @Deprecated
    private Long reviewedBy;

    /**
     * @deprecated 审核时间使用 material_review 表。此字段保留用于数据迁移兼容。
     */
    @Deprecated
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /**
     * 判断材料是否已提交。
     */
    public boolean isSubmitted() {
        return this.currentVersionId != null;
    }
}
