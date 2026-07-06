package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.dto.project.AddMemberRequest;
import com.eliza.aicompetition.dto.project.CreateProjectRequest;
import com.eliza.aicompetition.dto.project.ProjectCreateResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.entity.ProjectMember;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    private final CompetitionProjectMapper competitionProjectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMaterialMapper projectMaterialMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final NoticeService noticeService;

    public ProjectService(
        CompetitionProjectMapper competitionProjectMapper,
        ProjectMemberMapper projectMemberMapper,
        ProjectMaterialMapper projectMaterialMapper,
        ReviewRecordMapper reviewRecordMapper,
        SysUserMapper sysUserMapper,
        NoticeService noticeService
    ) {
        this.competitionProjectMapper = competitionProjectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.noticeService = noticeService;
    }

    @Transactional
    public ProjectCreateResponse createProject(CreateProjectRequest request) {
        CompetitionNotice notice = noticeService.getNoticeOrThrow(request.getNoticeId());
        validateUser(request.getLeaderId());
        if (request.getAdvisorId() != null) {
            validateUser(request.getAdvisorId());
        }
        if (!CollectionUtils.isEmpty(request.getMemberUserIds())) {
            request.getMemberUserIds().forEach(this::validateUser);
        }

        CompetitionProject project = new CompetitionProject();
        project.setNoticeId(request.getNoticeId());
        project.setLeaderId(request.getLeaderId());
        project.setProjectName(request.getProjectName().trim());
        project.setTeamName(request.getTeamName());
        project.setStatus("draft");
        project.setDeadline(request.getDeadline() == null ? notice.getDeadline() : request.getDeadline());
        project.setCompletionRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        competitionProjectMapper.insert(project);

        initializeMembers(project.getProjectId(), request);
        int initializedMaterialCount = initializeMaterials(project.getProjectId(), request.getNoticeId());
        ProjectProgressResponse progress = refreshProjectProgress(project.getProjectId());

        return new ProjectCreateResponse(
            project.getProjectId(),
            request.getProjectName().trim(),
            progress.getStatus(),
            progress.getCompletionRate(),
            initializedMaterialCount
        );
    }

    public ProjectDetailResponse getProjectDetail(Long projectId) {
        refreshProjectProgress(projectId);

        CompetitionProject project = getProjectOrThrow(projectId);
        CompetitionNotice notice = noticeService.getNoticeOrThrow(project.getNoticeId());
        SysUser leader = getUserOrThrow(project.getLeaderId());

        return ProjectDetailResponse.builder()
            .projectId(project.getProjectId())
            .noticeId(project.getNoticeId())
            .noticeTitle(notice.getTitle())
            .leaderId(project.getLeaderId())
            .leaderName(leader.getRealName())
            .projectName(project.getProjectName())
            .teamName(project.getTeamName())
            .status(project.getStatus())
            .deadline(project.getDeadline())
            .completionRate(project.getCompletionRate())
            .members(projectMemberMapper.findMemberViewsByProjectId(projectId))
            .materials(projectMaterialMapper.findLatestDetailsByProjectId(projectId))
            .reviewRecords(reviewRecordMapper.findReviewViewsByProjectId(projectId))
            .build();
    }

    @Transactional
    public ProjectProgressResponse refreshProjectProgress(Long projectId) {
        CompetitionProject project = getProjectOrThrow(projectId);
        List<ProjectMaterialView> materials = projectMaterialMapper.findLatestDetailsByProjectId(projectId);

        List<ProjectMaterialView> requiredMaterials = materials.stream()
            .filter(item -> Integer.valueOf(1).equals(item.getRequiredFlag()))
            .toList();

        int requiredTotal = requiredMaterials.size();
        int submittedTotal = (int) requiredMaterials.stream()
            .filter(item -> "submitted".equalsIgnoreCase(item.getSubmitStatus()))
            .count();

        List<String> missingMaterials = requiredMaterials.stream()
            .filter(item -> !"submitted".equalsIgnoreCase(item.getSubmitStatus()))
            .map(ProjectMaterialView::getRequirementName)
            .toList();

        BigDecimal completionRate = requiredTotal == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(submittedTotal * 100.0 / requiredTotal).setScale(2, RoundingMode.HALF_UP);

        String status;
        if (requiredTotal > 0 && submittedTotal == requiredTotal) {
            status = "ready";
        } else if (submittedTotal > 0) {
            status = "incomplete";
        } else {
            status = "draft";
        }

        project.setCompletionRate(completionRate);
        project.setStatus(status);
        competitionProjectMapper.updateById(project);

        return ProjectProgressResponse.builder()
            .projectId(projectId)
            .projectName(project.getProjectName())
            .status(status)
            .deadline(project.getDeadline())
            .requiredTotal(requiredTotal)
            .submittedTotal(submittedTotal)
            .missingTotal(missingMaterials.size())
            .completionRate(completionRate)
            .missingMaterials(missingMaterials)
            .build();
    }

    public ProjectProgressResponse getProgress(Long projectId) {
        return refreshProjectProgress(projectId);
    }

    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        // Ensure project exists
        getProjectOrThrow(projectId);
        // Ensure user exists
        validateUser(request.getUserId());

        // Check for duplicate membership
        long count = projectMemberMapper.selectCount(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, request.getUserId())
        );
        if (count > 0) {
            throw new BusinessException("该用户已是项目成员，不可重复添加");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(request.getUserId());
        member.setMemberRole(request.getMemberRole());
        member.setJoinTime(LocalDateTime.now());
        try {
            projectMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            // User was previously a member but logically deleted — reactivate
            int updated = projectMemberMapper.reactivateMember(
                projectId, request.getUserId(), request.getMemberRole());
            if (updated == 0) {
                throw new BusinessException("该用户已是项目成员，不可重复添加");
            }
        }
    }

    @Transactional
    public void removeMember(Long projectId, Long memberId) {
        // Ensure project exists
        getProjectOrThrow(projectId);

        ProjectMember member = projectMemberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw new BusinessException("项目成员记录不存在");
        }
        if ("leader".equals(member.getMemberRole())) {
            throw new BusinessException("项目负责人不可移除");
        }

        projectMemberMapper.deleteById(memberId);
    }

    public CompetitionProject getProjectOrThrow(Long projectId) {
        CompetitionProject project = competitionProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("Project not found: projectId=" + projectId);
        }
        return project;
    }

    /**
     * 获取当前用户参与的所有项目列表（含进度和审核汇总）。
     * 适用于教师查看指导的项目、学生查看自己参与的项目。
     */
    public List<ProjectListView> getMyProjects(Long userId) {
        List<ProjectListView> projects = projectMemberMapper.findProjectListByUserId(userId);
        for (ProjectListView project : projects) {
            // Fill in member names
            List<String> memberNames = projectMemberMapper.findMemberViewsByProjectId(project.getProjectId())
                .stream()
                .map(m -> m.getRealName() + "（" + roleLabel(m.getMemberRole()) + "）")
                .toList();
            project.setMemberNames(memberNames);

            // Fill in submission/review counts
            List<ProjectMaterialView> materials = projectMaterialMapper.findLatestDetailsByProjectId(project.getProjectId());
            int total = materials.size();
            int submitted = (int) materials.stream()
                .filter(m -> "submitted".equalsIgnoreCase(m.getSubmitStatus()))
                .count();
            int reviewed = (int) materials.stream()
                .filter(m -> m.getReviewStatus() != null)
                .count();
            project.setSubmittedCount(submitted);
            project.setTotalCount(total);
            project.setReviewedCount(reviewed);
        }
        return projects;
    }

    /**
     * 获取项目各材料的审核状态汇总。
     */
    public List<ProjectMaterialView> getProjectReviewStatus(Long projectId) {
        // Ensure project exists
        getProjectOrThrow(projectId);
        return projectMaterialMapper.findLatestDetailsByProjectId(projectId);
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "leader" -> "负责人";
            case "advisor" -> "指导教师";
            case "member" -> "成员";
            default -> role;
        };
    }

    private void initializeMembers(Long projectId, CreateProjectRequest request) {
        saveMember(projectId, request.getLeaderId(), "leader");

        Set<Long> memberIds = new LinkedHashSet<>();
        if (request.getAdvisorId() != null && !request.getAdvisorId().equals(request.getLeaderId())) {
            saveMember(projectId, request.getAdvisorId(), "advisor");
        }
        if (!CollectionUtils.isEmpty(request.getMemberUserIds())) {
            memberIds.addAll(request.getMemberUserIds());
        }
        memberIds.remove(request.getLeaderId());
        memberIds.remove(request.getAdvisorId());
        memberIds.forEach(userId -> saveMember(projectId, userId, "member"));
    }

    private int initializeMaterials(Long projectId, Long noticeId) {
        List<MaterialRequirement> requirements = noticeService.findRequirementsByNoticeId(noticeId);
        for (MaterialRequirement requirement : requirements) {
            ProjectMaterial projectMaterial = new ProjectMaterial();
            projectMaterial.setProjectId(projectId);
            projectMaterial.setRequirementId(requirement.getRequirementId());
            projectMaterial.setSubmitStatus("pending");
            projectMaterial.setVersionNo(1);
            projectMaterial.setRemark("Initialized by system, waiting for upload");
            projectMaterialMapper.insert(projectMaterial);
        }
        return requirements.size();
    }

    private void saveMember(Long projectId, Long userId, String role) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setJoinTime(LocalDateTime.now());
        projectMemberMapper.insert(member);
    }

    private void validateUser(Long userId) {
        getUserOrThrow(userId);
    }

    private SysUser getUserOrThrow(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("User not found: userId=" + userId);
        }
        return user;
    }
}
