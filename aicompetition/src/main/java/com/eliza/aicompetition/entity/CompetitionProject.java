package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private String status;
    private LocalDateTime deadline;
    private BigDecimal completionRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
