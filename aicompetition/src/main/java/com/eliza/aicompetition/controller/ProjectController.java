package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.project.AddMemberRequest;
import com.eliza.aicompetition.dto.project.CreateProjectRequest;
import com.eliza.aicompetition.dto.project.ProjectCreateResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
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
}
