package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
