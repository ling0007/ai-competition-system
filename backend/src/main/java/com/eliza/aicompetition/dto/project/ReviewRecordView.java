package com.eliza.aicompetition.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewRecordView {
    private Long reviewId;
    private String reviewType;
    private String reviewResult;
    private String reviewComment;
    private String reviewerName;
    private LocalDateTime createdAt;
}
