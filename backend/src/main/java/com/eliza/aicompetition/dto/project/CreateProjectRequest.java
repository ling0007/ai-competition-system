package com.eliza.aicompetition.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建项目请求 DTO。
 * <p>
 * <b>安全注意</b>：leaderId 已改为可选字段。
 * 学生创建项目时不需要传 leaderId，后端自动从 JWT 获取当前用户作为负责人。
 * 管理员可以为他人创建项目（需要传 leaderId 参数）。
 * </p>
 */
@Data
public class CreateProjectRequest {
    @NotNull(message = "noticeId 不能为空")
    private Long noticeId;

    /**
     * 项目负责人 ID。
     * <ul>
     *   <li>普通学生：不传此字段，后端自动使用当前登录用户</li>
     *   <li>管理员：可传入任意用户 ID，为他人创建项目</li>
     * </ul>
     */
    private Long leaderId;

    @NotBlank(message = "projectName 不能为空")
    private String projectName;

    private String teamName;
    private LocalDateTime deadline;
    private Long advisorId;
    private List<Long> memberUserIds;
}
