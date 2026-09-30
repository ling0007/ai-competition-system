package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.dto.agent.ProjectAiCheckView;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.ProjectService;
import com.eliza.aicompetition.service.DashboardCacheService;
import com.eliza.aicompetition.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘数据聚合接口。
 * <p>
 * 将前端首页需要展示的多张表数据聚合到一个请求中返回，
 * 减少前端首次加载时的网络请求次数。
 * </p>
 * <p>
 * <b>安全注意</b>：用户身份通过 {@link SecurityUtils} 从 JWT 获取，
 * 不再手动解析 HttpServletRequest 中的 Authorization 头。
 * </p>
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final CompetitionProjectMapper competitionProjectMapper;
    private final FileAssetMapper fileAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final NoticeService noticeService;
    private final ProjectService projectService;
    private final DashboardCacheService dashboardCacheService;
    private final AgentService agentService;

    public DashboardController(
        CompetitionNoticeMapper competitionNoticeMapper,
        CompetitionProjectMapper competitionProjectMapper,
        FileAssetMapper fileAssetMapper,
        SysUserMapper sysUserMapper,
        ProjectMemberMapper projectMemberMapper,
        NoticeService noticeService,
        ProjectService projectService,
        DashboardCacheService dashboardCacheService,
        AgentService agentService
    ) {
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.competitionProjectMapper = competitionProjectMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.sysUserMapper = sysUserMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.noticeService = noticeService;
        this.projectService = projectService;
        this.dashboardCacheService = dashboardCacheService;
        this.agentService = agentService;
    }

    /**
     * Dashboard 引导数据。
     * <p>
     * 通过 SecurityUtils 从 JWT 获取当前用户 ID。
     * 如果用户未登录（例如访问了放行路径），userId 为 null，
     * Dashboard 仍可展示公共数据（通知列表等）。
     * </p>
     */
    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap() {
        log.info("Dashboard bootstrap requested");

        // 从 Spring Security 的 SecurityContext 中获取当前用户 ID
        // SecurityUtils 内部使用 ThreadLocal，天然线程安全
        Long userId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : null;

        String role = userId == null ? null : SecurityUtils.getCurrentUserRole();
        Map<String, Object> global = dashboardCacheService.getGlobal(userId, role);
        if (global == null) {
            long revision = dashboardCacheService.epoch();
            global = loadGlobal(role);
            dashboardCacheService.cacheGlobal(userId, role, revision, global);
        }

        // Project membership and every project-derived field are read from DB on each request.
        // A removed member must never receive an old project snapshot from Redis.
        CompetitionProject userProject = null;
        if (userId != null) {
            String memberRole = "teacher".equalsIgnoreCase(role) ? "advisor" : null;
            List<Long> ids = projectMemberMapper.findProjectIdsByUserId(userId, memberRole);
            if (!ids.isEmpty()) userProject = competitionProjectMapper.selectById(ids.get(0));
        }
        ProjectDetailResponse projectDetail = null;
        ProjectProgressResponse progress = null;
        Map<String, Object> aiCheck = null;
        if (userProject != null) {
            projectService.checkProjectReadAccess(userProject.getProjectId());
            progress = projectService.refreshProjectProgress(userProject.getProjectId());
            projectDetail = projectService.getProjectDetail(userProject.getProjectId());
            aiCheck = currentAiCheck(userProject.getProjectId(), progress.getCompletionRate(),
                userProject.getProjectName());
            if (aiCheck != null) aiCheck.put("missingMaterials", progress.getMissingMaterials());
        }
        Map<String, Object> data = new LinkedHashMap<>(global);
        data.put("projectDetail", projectDetail);
        data.put("progress", progress);
        data.put("aiCheck", aiCheck);
        return ApiResponse.success(data);
    }

    private Map<String, Object> loadGlobal(String role) {
        // 1. User options
        List<Map<String, Object>> userOptions = sysUserMapper.selectList(null).stream()
            .map(user -> {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("value", user.getUserId());
                option.put("label", user.getRealName() + " · "
                    + ("teacher".equals(user.getRole()) ? "指导教师" : "学生"));
                option.put("role", user.getRole());
                return option;
            })
            .toList();

        // 2. Notice (latest) + notice options
        boolean admin = "admin".equalsIgnoreCase(role);
        LambdaQueryWrapper<CompetitionNotice> noticeQuery = new LambdaQueryWrapper<CompetitionNotice>()
            .eq(!admin, CompetitionNotice::getPublishStatus, "PUBLISHED")
            .orderByDesc(CompetitionNotice::getNoticeId).last("limit 1");
        CompetitionNotice latestNotice = competitionNoticeMapper.selectOne(noticeQuery);

        Map<String, Object> noticeView = null;
        List<Map<String, Object>> noticeOptions = List.of();

        if (latestNotice != null) {
            // Build notice view
            noticeView = new LinkedHashMap<>();
            noticeView.put("noticeId", latestNotice.getNoticeId());
            noticeView.put("title", latestNotice.getTitle());
            noticeView.put("organizer", latestNotice.getOrganizer());
            noticeView.put("deadline", latestNotice.getDeadline());
            noticeView.put("targetGroup", latestNotice.getTargetGroup());
            noticeView.put("rawText", latestNotice.getRawText());
            noticeView.put("aiSummary", latestNotice.getAiSummary());
            noticeView.put("fileId", latestNotice.getNoticeFileId());

            // File name
            String fileName = "";
            if (latestNotice.getNoticeFileId() != null) {
                FileAsset fileAsset = fileAssetMapper.selectById(latestNotice.getNoticeFileId());
                fileName = fileAsset != null ? fileAsset.getFileName() : "";
            }
            noticeView.put("fileName", fileName);

            // Material requirements
            List<MaterialRequirement> requirements = noticeService.findRequirementsByNoticeId(latestNotice.getNoticeId());
            List<String> requirementNames = requirements.stream()
                .map(MaterialRequirement::getRequirementName)
                .toList();
            noticeView.put("materialRequirements", requirementNames);

            // Draft notice metadata is visible only to admins.
            List<CompetitionNotice> allNotices = competitionNoticeMapper.selectList(
                new LambdaQueryWrapper<CompetitionNotice>()
                    .eq(!admin, CompetitionNotice::getPublishStatus, "PUBLISHED"));
            noticeOptions = allNotices.stream()
                .map(n -> {
                    Map<String, Object> opt = new LinkedHashMap<>();
                    opt.put("value", n.getNoticeId());
                    opt.put("label", n.getTitle());
                    opt.put("deadline", n.getDeadline());
                    return opt;
                })
                .toList();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notice", noticeView);
        data.put("noticeOptions", noticeOptions);
        data.put("userOptions", userOptions);
        return data;
    }

    private Map<String, Object> currentAiCheck(Long projectId, Object completionRate, Object projectName) {
        List<ProjectAiCheckView> checks = agentService.listProjectChecks(projectId);
        if (checks.isEmpty() || checks.get(0).stale()) return null;
        ProjectAiCheckView latest = checks.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectName", projectName);
        result.put("reviewResult", latest.result());
        result.put("reviewComment", latest.issueSummary());
        result.put("completionRate", completionRate);
        return result;
    }
}
