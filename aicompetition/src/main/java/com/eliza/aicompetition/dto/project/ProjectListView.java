package com.eliza.aicompetition.dto.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListView {
    private Long projectId;
    private String projectName;
    private String teamName;
    private String status;
    private BigDecimal completionRate;
    private LocalDateTime deadline;
    private String leaderName;
    private String noticeTitle;
    private List<String> memberNames;
    private int submittedCount;
    private int totalCount;
    private int reviewedCount;
}
