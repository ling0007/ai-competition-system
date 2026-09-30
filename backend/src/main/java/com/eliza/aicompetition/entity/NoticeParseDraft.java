package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 解析草稿 —— AI 解析通知的结果先写入此表，
 * 管理员确认后才正式写入 {@link CompetitionNotice} 和 {@link MaterialRequirement}。
 */
@Data
@TableName("notice_parse_draft")
public class NoticeParseDraft {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联通知 ID */
    private Long noticeId;

    /** AI 提取的标题 */
    private String aiTitle;

    /** AI 提取的主办方 */
    private String aiOrganizer;

    /** AI 提取的截止时间 */
    private LocalDateTime aiDeadline;

    /** AI 提取的面向对象 */
    private String aiTargetGroup;

    /** AI 提取的关键内容 */
    private String aiKeyPoints;

    /** AI 提取的材料要求（JSON 数组字符串） */
    private String aiMaterialsJson;

    /** LLM 原始返回 JSON（完整保留，用于调试和人工对比） */
    private String rawAiResponse;

    /**
     * 草稿状态：PENDING-待确认, CONFIRMED-已确认, REJECTED-已拒绝。
     */
    private String status;

    /** 触发解析人 ID */
    private Long createdBy;

    /** 确认人 ID */
    private Long confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
