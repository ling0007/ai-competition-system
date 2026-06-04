package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.project.AddMemberRequest;
import com.eliza.aicompetition.dto.project.CreateProjectRequest;
import com.eliza.aicompetition.dto.project.ProjectCreateResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.service.ProjectService;
import com.eliza.aicompetition.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;
    private final JwtUtil jwtUtil;

    public ProjectController(ProjectService projectService, JwtUtil jwtUtil) {
        this.projectService = projectService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/create")
    public ApiResponse<ProjectCreateResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
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
     * 获取当前用户参与的所有项目列表。
     * 通过 JWT 自动识别用户身份，教师和学生均可使用。
     */
    @GetMapping("/my-projects")
    public ApiResponse<List<ProjectListView>> getMyProjects(HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ApiResponse.fail(401, "未登录或令牌已过期");
        }
        return ApiResponse.success(projectService.getMyProjects(userId));
    }

    /**
     * 获取项目所有材料的审核状态。
     */
    @GetMapping("/{projectId}/review-status")
    public ApiResponse<List<ProjectMaterialView>> getProjectReviewStatus(@PathVariable Long projectId) {
        return ApiResponse.success(projectService.getProjectReviewStatus(projectId));
    }

    private Long extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                return jwtUtil.getUserId(token);
            }
        }
        return null;
    }
}
