package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.eliza.aicompetition.common.enums.NoticeParseStatus;
import com.eliza.aicompetition.common.enums.NoticePublishStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("competition_notice")
public class CompetitionNotice {
    @TableId(value = "notice_id", type = IdType.AUTO)
    private Long noticeId;
    private String title;
    private String organizer;
    private LocalDateTime deadline;
    private String targetGroup;
    private String rawText;
    private String aiSummary;
    private Long noticeFileId;
    private Long createdBy;

    /**
     * 申报类型：COMPETITION-竞赛, RESEARCH-科研项目, SCHOLARSHIP-奖学金, OTHER-其他。
     */
    private String noticeType;

    /**
     * AI解析状态（枚举 {@link NoticeParseStatus}）：
     * DRAFT-待解析, PARSING-解析中, PARSED-已解析, FAILED-解析失败。
     */
    private String parseStatus;

    /**
     * 发布状态（枚举 {@link NoticePublishStatus}）：
     * DRAFT-草稿, PUBLISHED-已发布, ARCHIVED-已归档。
     */
    private String publishStatus;

    /** 确认人ID（管理员确认AI解析结果时记录） */
    private Long confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    // ==================== 领域方法：解析状态 ====================

    /**
     * 开始解析 —— DRAFT/FAILED/PARSED → PARSING。
     */
    public void startParsing() {
        NoticeParseStatus current = NoticeParseStatus.valueOf(this.parseStatus);
        this.parseStatus = current.startParsing().name();
    }

    /**
     * 标记解析完成 —— PARSING → PARSED。
     */
    public void markParsed() {
        NoticeParseStatus current = NoticeParseStatus.valueOf(this.parseStatus);
        this.parseStatus = current.markParsed().name();
    }

    /**
     * 标记解析失败 —— PARSING → FAILED。
     */
    public void markParseFailed() {
        NoticeParseStatus current = NoticeParseStatus.valueOf(this.parseStatus);
        this.parseStatus = current.markParseFailed().name();
    }

    /**
     * 人工录入完成 —— FAILED → PARSED。
     */
    public void completeManualEntry() {
        NoticeParseStatus current = NoticeParseStatus.valueOf(this.parseStatus);
        this.parseStatus = current.completeManualEntry().name();
    }

    /**
     * 重新解析 —— PARSED → PARSING。
     */
    public void restartParsing() {
        NoticeParseStatus current = NoticeParseStatus.valueOf(this.parseStatus);
        this.parseStatus = current.restartParsing().name();
    }

    /**
     * 确认解析内容（记录确认人和时间）。
     */
    public void confirmParsedContent(Long confirmedBy) {
        if (this.parseStatus == null || !NoticeParseStatus.PARSED.name().equals(this.parseStatus)) {
            throw new com.eliza.aicompetition.exception.InvalidStatusTransitionException(
                "NoticeParseStatus", this.parseStatus, "confirm");
        }
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
    }

    // ==================== 领域方法：发布状态 ====================

    /**
     * 发布通知 —— DRAFT → PUBLISHED。
     * 调用前必须确保 parseStatus == PARSED && confirmedAt != null。
     */
    public void publish() {
        NoticePublishStatus current = NoticePublishStatus.valueOf(this.publishStatus);
        this.publishStatus = current.publish().name();
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * 归档通知 —— PUBLISHED → ARCHIVED。
     */
    public void archive() {
        NoticePublishStatus current = NoticePublishStatus.valueOf(this.publishStatus);
        this.publishStatus = current.archive().name();
    }
}
