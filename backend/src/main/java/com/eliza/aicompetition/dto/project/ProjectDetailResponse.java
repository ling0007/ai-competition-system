package com.eliza.aicompetition.dto.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectDetailResponse {
    private Long projectId;
    private Long noticeId;
    private String noticeTitle;
    private Long leaderId;
    private String leaderName;
    private String projectName;
    private String teamName;
    private String status;
    private LocalDateTime deadline;
    private BigDecimal completionRate;
    private List<ProjectMemberView> members;
    private List<ProjectMaterialView> materials;
    private List<ReviewRecordView> reviewRecords;
}
