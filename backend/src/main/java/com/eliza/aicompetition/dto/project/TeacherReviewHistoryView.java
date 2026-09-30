package com.eliza.aicompetition.dto.project;

import lombok.Data;
import java.time.LocalDateTime;

/** Append-only material and project decisions made by the authenticated reviewer. */
@Data
public class TeacherReviewHistoryView {
    private Long reviewId;
    private Long projectId;
    private String projectName;
    private String requirementName;
    private Integer versionNo;
    private String decision;
    private String comment;
    private String reviewerName;
    private LocalDateTime createdAt;
    private String reviewType;
}
