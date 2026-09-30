package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.eliza.aicompetition.common.enums.ProjectStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("competition_project")
public class CompetitionProject {
    @TableId(value = "project_id", type = IdType.AUTO)
    private Long projectId;
    private Long noticeId;
    private Long leaderId;
    private String projectName;
    private String teamName;

    /**
     * 项目状态（枚举 {@link ProjectStatus}）。
     * DRAFT / UNDER_REVIEW / REVISION_REQUIRED / APPROVED。
     * 兼容旧 DB 存储过程使用的值，通过 ProjectStatus.fromDbValue() 映射。
     */
    private String status;

    private LocalDateTime deadline;
    private BigDecimal completionRate;

    /** 乐观锁版本号 */
    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    // ==================== 领域方法 ====================

    /**
     * 提交审核 —— DRAFT/REVISION_REQUIRED → UNDER_REVIEW。
     */
    public void submitForReview() {
        ProjectStatus current = ProjectStatus.fromDbValue(this.status);
        this.status = current.submitForReview().name();
    }

    /**
     * 审核通过 —— UNDER_REVIEW → APPROVED。
     */
    public void approve() {
        ProjectStatus current = ProjectStatus.fromDbValue(this.status);
        this.status = current.approve().name();
    }

    /**
     * 退回修改 —— UNDER_REVIEW → REVISION_REQUIRED。
     */
    public void requestRevision() {
        ProjectStatus current = ProjectStatus.fromDbValue(this.status);
        this.status = current.requestRevision().name();
    }

    /**
     * 重新提交 —— REVISION_REQUIRED → UNDER_REVIEW。
     */
    public void resubmit() {
        ProjectStatus current = ProjectStatus.fromDbValue(this.status);
        this.status = current.resubmit().name();
    }

    /**
     * 获取当前项目状态的枚举值。
     */
    public ProjectStatus getProjectStatus() {
        return ProjectStatus.fromDbValue(this.status);
    }
}
