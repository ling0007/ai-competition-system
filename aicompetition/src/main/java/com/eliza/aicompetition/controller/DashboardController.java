package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.agent.MaterialCheckResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.ReviewRecord;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates dashboard bootstrap data so the frontend can load
 * all required reference data in a single request.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final CompetitionProjectMapper competitionProjectMapper;
    private final FileAssetMapper fileAssetMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final NoticeService noticeService;
    private final ProjectService projectService;

    public DashboardController(
        CompetitionNoticeMapper competitionNoticeMapper,
        CompetitionProjectMapper competitionProjectMapper,
        FileAssetMapper fileAssetMapper,
        ReviewRecordMapper reviewRecordMapper,
        SysUserMapper sysUserMapper,
        NoticeService noticeService,
        ProjectService projectService
    ) {
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.competitionProjectMapper = competitionProjectMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.noticeService = noticeService;
        this.projectService = projectService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap() {
        log.info("Dashboard bootstrap requested");

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
        LambdaQueryWrapper<CompetitionNotice> noticeQuery = new LambdaQueryWrapper<CompetitionNotice>()
            .orderByDesc(CompetitionNotice::getNoticeId)
            .last("limit 1");
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

            // Notice options (all notices)
            List<CompetitionNotice> allNotices = competitionNoticeMapper.selectList(null);
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

        // 3. Latest project
        LambdaQueryWrapper<CompetitionProject> projectQuery = new LambdaQueryWrapper<CompetitionProject>()
            .orderByDesc(CompetitionProject::getProjectId)
            .last("limit 1");
        CompetitionProject latestProject = competitionProjectMapper.selectOne(projectQuery);

        ProjectDetailResponse projectDetail = null;
        ProjectProgressResponse progress = null;
        Map<String, Object> aiCheck = null;

        if (latestProject != null) {
            try {
                // Refresh progress first (ensures status is up-to-date)
                progress = projectService.refreshProjectProgress(latestProject.getProjectId());
                projectDetail = projectService.getProjectDetail(latestProject.getProjectId());
            } catch (Exception e) {
                log.warn("Failed to load project detail for projectId={}: {}", latestProject.getProjectId(), e.getMessage());
            }

            // Latest AI review
            LambdaQueryWrapper<ReviewRecord> reviewQuery = new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getProjectId, latestProject.getProjectId())
                .eq(ReviewRecord::getReviewType, "ai")
                .orderByDesc(ReviewRecord::getReviewId)
                .last("limit 1");
            ReviewRecord latestReview = reviewRecordMapper.selectOne(reviewQuery);

            if (latestReview != null && progress != null) {
                aiCheck = new LinkedHashMap<>();
                aiCheck.put("projectId", latestProject.getProjectId());
                aiCheck.put("projectName", latestProject.getProjectName());
                aiCheck.put("reviewResult", latestReview.getReviewResult());
                aiCheck.put("reviewComment", latestReview.getReviewComment());
                aiCheck.put("completionRate", progress.getCompletionRate());
                aiCheck.put("missingMaterials", progress.getMissingMaterials());
            }
        }

        // Assemble response
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notice", noticeView);
        data.put("noticeOptions", noticeOptions);
        data.put("userOptions", userOptions);
        data.put("projectDetail", projectDetail);
        data.put("progress", progress);
        data.put("aiCheck", aiCheck);

        return ApiResponse.success(data);
    }
}
