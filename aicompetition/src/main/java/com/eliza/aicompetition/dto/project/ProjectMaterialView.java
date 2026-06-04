package com.eliza.aicompetition.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMaterialView {
    private Long materialId;
    private Long requirementId;
    private String requirementName;
    private Integer requiredFlag;
    private String description;
    private String submitStatus;
    private Long fileId;
    private String fileName;
    private Integer versionNo;
    private String remark;
    private LocalDateTime submittedAt;
    private String reviewStatus;
    private String reviewComment;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
}
