package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <h1>AI 材料检查记录实体</h1>
 *
 * <p>每次 AI 检查新增一条记录，不覆盖旧记录。</p>
 *
 * <h2>关键字段</h2>
 * <ul>
 *   <li>{@code materialSnapshot} — 检查时使用的材料版本ID集合，用于判断结果是否仍然有效</li>
 *   <li>{@code result} — 映射 {@link com.eliza.aicompetition.common.enums.AiCheckResult}</li>
 * </ul>
 */
@Data
@TableName("project_ai_check")
public class ProjectAiCheck {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属项目ID */
    private Long projectId;

    /** 关联的Agent任务ID */
    private Long agentTaskId;

    /** 检查时使用的材料版本ID集合（JSON数组或逗号分隔） */
    private String materialSnapshot;

    /** AI检查结果：PASSED / WARNING */
    private String result;

    /** 问题摘要 */
    private String issueSummary;

    /** 详细问题JSON */
    private String issueDetailJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
