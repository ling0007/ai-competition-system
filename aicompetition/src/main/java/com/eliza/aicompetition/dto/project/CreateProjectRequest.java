package com.eliza.aicompetition.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateProjectRequest {
    @NotNull(message = "noticeId 不能为空")
    private Long noticeId;

    @NotNull(message = "leaderId 不能为空")
    private Long leaderId;

    @NotBlank(message = "projectName 不能为空")
    private String projectName;

    private String teamName;
    private LocalDateTime deadline;
    private Long advisorId;
    private List<Long> memberUserIds;
}
