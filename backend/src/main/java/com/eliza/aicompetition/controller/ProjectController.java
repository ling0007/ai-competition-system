package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.project.AddMemberRequest;
import com.eliza.aicompetition.dto.project.CreateProjectRequest;
import com.eliza.aicompetition.dto.project.ProjectCreateResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目申报管理接口。
 * <p>
 * <b>安全注意</b>：用户身份通过 {@link SecurityUtils} 从 JWT 获取，
 * 不再手动解析 HttpServletRequest 中的 Authorization 头。
 * </p>
 */
@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 创建申报项目。
     * <p>
     * 如果请求体未传 leaderId，则默认使用当前登录用户作为项目负责人。
     * 管理员可以通过传 leaderId 为其他用户创建项目。
     * </p>
     */
    @PostMapping("/create")
    public ApiResponse<ProjectCreateResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        // 如果前端未传 leaderId，默认使用当前登录用户
        if (request.getLeaderId() == null) {
            request.setLeaderId(SecurityUtils.getCurrentUserId());
        }
        return ApiResponse.success("项目创建成功", projectService.createProject(request));
    }

    @GetMapping("/detail/{projectId}")
    public ApiResponse<ProjectDetailResponse> getProjectDetail(@PathVariable Long projectId) {
        return ApiResponse.success(projectService.getProjectDetail(projectId));
    }

    @GetMapping("/progress/{projectId}")
    public ApiResponse<ProjectProgressResponse> getProgress(@PathVariable Long projectId) {
        return ApiResponse.success(projectService.getProgress(projectId));
    }

    @PostMapping("/{projectId}/members")
    public ApiResponse<Void> addMember(@PathVariable Long projectId, @Valid @RequestBody AddMemberRequest request) {
        projectService.addMember(projectId, request);
        return ApiResponse.success("成员添加成功", null);
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        projectService.removeMember(projectId, memberId);
        return ApiResponse.success("成员移除成功", null);
    }

    /**
     * 管理员项目总览 —— 查询平台内所有项目（分页 + 筛选）。
     * <p>
     * <b>权限要求</b>：仅 ADMIN 角色可访问。学生访问返回 403。
     * </p>
     * <p>
     * 支持按项目名称/负责人/通知标题模糊搜索、项目状态筛选、所属通知筛选。
     * 默认按创建时间倒序排列。
     * </p>
     */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<com.eliza.aicompetition.common.PageResult<ProjectListView>> getAllProjects(
        @RequestParam(required = false, defaultValue = "") String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long noticeId,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(
            projectService.getAllProjectsPaginated(keyword, status, noticeId, pageNum, pageSize));
    }

    /**
     * 获取当前用户参与的项目列表（分页 + 筛选）。
     * 通过 SecurityUtils 从 JWT 自动识别用户身份。
     */
    @GetMapping("/my-projects")
    public ApiResponse<com.eliza.aicompetition.common.PageResult<ProjectListView>> getMyProjects(
        @RequestParam(required = false, defaultValue = "") String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime deadlineBefore,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(
            projectService.getMyProjectsPaginated(userId, keyword, status, deadlineBefore, pageNum, pageSize));
    }

    /**
     * 获取项目所有材料的审核状态。
     */
    @GetMapping("/{projectId}/review-status")
    public ApiResponse<List<ProjectMaterialView>> getProjectReviewStatus(@PathVariable Long projectId) {
        return ApiResponse.success(projectService.getProjectReviewStatus(projectId));
    }

    @GetMapping("/reviews/my-history")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<com.eliza.aicompetition.common.PageResult<com.eliza.aicompetition.dto.project.TeacherReviewHistoryView>> myReviewHistory(
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(projectService.getMyReviewHistory(pageNum, pageSize));
    }

    // ==================== 项目状态流转接口 ====================

    /** 首次提交项目审核 —— DRAFT → UNDER_REVIEW；退回后的项目使用 resubmit。 */
    @PostMapping("/{projectId}/submit")
    public ApiResponse<Void> submitForReview(@PathVariable Long projectId) {
        projectService.submitForReview(projectId);
        return ApiResponse.success("项目已提交审核", null);
    }

    /** 项目指导教师或管理员审核通过 —— UNDER_REVIEW → APPROVED */
    @PutMapping("/{projectId}/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> approve(@PathVariable Long projectId) {
        projectService.approve(projectId);
        return ApiResponse.success("项目已审核通过", null);
    }

    /** 项目指导教师或管理员退回修改 —— UNDER_REVIEW → REVISION_REQUIRED */
    @PutMapping("/{projectId}/request-revision")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> requestRevision(
            @PathVariable Long projectId,
            @RequestParam(required = false) String reason) {
        projectService.requestRevision(projectId, reason);
        return ApiResponse.success("项目已退回修改", null);
    }

    /** 学生重新提交 —— REVISION_REQUIRED → UNDER_REVIEW */
    @PostMapping("/{projectId}/resubmit")
    public ApiResponse<Void> resubmit(@PathVariable Long projectId) {
        projectService.resubmit(projectId);
        return ApiResponse.success("项目已重新提交审核", null);
    }
}
