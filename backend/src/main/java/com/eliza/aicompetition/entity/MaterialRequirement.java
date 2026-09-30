package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_requirement")
public class MaterialRequirement {
    @TableId(value = "requirement_id", type = IdType.AUTO)
    private Long requirementId;
    private Long noticeId;
    private String requirementName;
    private Integer isRequired;
    private String description;
    private Integer sortNo;

    /**
     * 材料要求状态：ACTIVE-启用, INACTIVE-停用。
     * 当通知已有关联项目时，旧材料要求不可删除，只能停用。
     */
    private String status;

    /**
     * 材料要求来源：AI-智能解析生成, MANUAL-人工录入。
     */
    private String source;

    /** 材料要求版本号 */
    private Integer versionNo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
