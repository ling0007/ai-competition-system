package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <h1>材料审核记录实体</h1>
 *
 * <p>每次教师/管理员审核材料时新增一条记录，不覆盖旧数据。</p>
 *
 * <h2>关键设计</h2>
 * <ul>
 *   <li>审核决定绑定 {@code materialVersionId}，而不是只绑定材料实体</li>
 *   <li>学生上传新版本后，旧版本审核记录保留，新版本自然处于未审核状态</li>
 *   <li>不允许修改或覆盖旧审核记录</li>
 *   <li>{@code decision} — 映射 {@link com.eliza.aicompetition.common.enums.MaterialReviewDecision}</li>
 * </ul>
 */
@Data
@TableName("material_review")
public class MaterialReview {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 被审核的材料ID */
    private Long materialId;

    /** 被审核的材料版本ID（必须绑定版本） */
    private Long materialVersionId;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核决定：APPROVED / REVISION_REQUIRED */
    private String decision;

    /** 审核意见 */
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
