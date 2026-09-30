package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.common.enums.MaterialCompleteness;
import com.eliza.aicompetition.common.enums.NoticePublishStatus;
import com.eliza.aicompetition.common.enums.ProjectStatus;
import com.eliza.aicompetition.dto.project.AddMemberRequest;
import com.eliza.aicompetition.dto.project.CreateProjectRequest;
import com.eliza.aicompetition.dto.project.ProjectCreateResponse;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.dto.project.TeacherReviewHistoryView;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.entity.ProjectMember;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 项目管理 Service。
 * <p>
 * <b>权限校验（P0-3）</b>：所有涉及具体项目 ID 的操作都必须校验
 * 当前用户是否有权访问该项目。校验规则：
 * <ul>
 *   <li>学生项目成员（含负责人/成员）→ 可访问</li>
 *   <li>TEACHER 仅在作为项目 advisor 时可访问和审核</li>
 *   <li>ADMIN 角色 → 无条件访问</li>
 *   <li>其他人 → 抛出 BusinessException("无权访问该项目")</li>
 * </ul>
 * </p>
 */
@Service
public class ProjectService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectService.class);
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_TEACHER = "teacher";
    private static final String MEMBER_ROLE_ADVISOR = "advisor";
    private static final String MEMBER_ROLE_MEMBER = "member";

    private final CompetitionProjectMapper competitionProjectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMaterialMapper projectMaterialMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final NoticeService noticeService;
    private final NotifyMessageMapper notifyMessageMapper;

    public ProjectService(
        CompetitionProjectMapper competitionProjectMapper,
        ProjectMemberMapper projectMemberMapper,
        ProjectMaterialMapper projectMaterialMapper,
        ReviewRecordMapper reviewRecordMapper,
        SysUserMapper sysUserMapper,
        NoticeService noticeService,
        NotifyMessageMapper notifyMessageMapper
    ) {
        this.competitionProjectMapper = competitionProjectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.noticeService = noticeService;
        this.notifyMessageMapper = notifyMessageMapper;
    }

    @Transactional
    public ProjectCreateResponse createProject(CreateProjectRequest request) {
        // P0-3: 权限校验 —— 学生只能为自己创建项目，管理员可以为他人创建
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentRole = SecurityUtils.getCurrentUserRole();
        if (!"student".equalsIgnoreCase(currentRole) && !"admin".equalsIgnoreCase(currentRole)) {
            throw new BusinessException(403, "只有学生或管理员可以创建项目");
        }
        if (!"admin".equalsIgnoreCase(currentRole)
                && !currentUserId.equals(request.getLeaderId())) {
            throw new BusinessException("只能为自己创建项目，管理员可以为他人创建");
        }

        CompetitionNotice notice = noticeService.getNoticeOrThrow(request.getNoticeId());
        if (!NoticePublishStatus.PUBLISHED.name().equals(notice.getPublishStatus())) {
            throw new BusinessException(409, "通知尚未发布或已归档，无法创建项目");
        }
        validateUser(request.getLeaderId());
        if (request.getAdvisorId() != null) {
            validateAdvisorUser(request.getAdvisorId());
            if (request.getAdvisorId().equals(request.getLeaderId())) {
                throw new BusinessException("项目负责人不能同时作为指导教师");
            }
        }
        if (!CollectionUtils.isEmpty(request.getMemberUserIds())) {
            request.getMemberUserIds().forEach(this::validateUser);
        }

        CompetitionProject project = new CompetitionProject();
        project.setNoticeId(request.getNoticeId());
        project.setLeaderId(request.getLeaderId());
        project.setProjectName(request.getProjectName().trim());
        project.setTeamName(request.getTeamName());
        project.setStatus(ProjectStatus.DRAFT.name());
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
        // P0-3: 校验当前用户是否有权访问该项目
        checkProjectReadAccess(projectId);
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
            .materials(findCurrentMaterialViews(projectId))
            .reviewRecords(reviewRecordMapper.findReviewViewsByProjectId(projectId))
            .build();
    }

    /**
     * 刷新项目进度 —— 只计算材料完成率，不修改项目生命周期状态。
     * <p>
     * 项目状态通过 {@link #submitForReview}, {@link #approve}, {@link #requestRevision}, {@link #resubmit}
     * 等业务方法修改，进度刷新不再混入状态变更。
     * </p>
     */
    @Transactional
    public ProjectProgressResponse refreshProjectProgress(Long projectId) {
        CompetitionProject project = getProjectOrThrow(projectId);
        List<ProjectMaterialView> materials = findCurrentMaterialViews(projectId);

        List<ProjectMaterialView> requiredMaterials = materials.stream()
            .filter(item -> Integer.valueOf(1).equals(item.getRequiredFlag()))
            .toList();

        int requiredTotal = requiredMaterials.size();
        // 上传状态由当前版本及其文件共同派生，避免空占位记录被计为完成。
        int submittedTotal = (int) requiredMaterials.stream()
            .filter(item -> Boolean.TRUE.equals(item.getUploaded()))
            .count();

        List<String> missingMaterials = requiredMaterials.stream()
            .filter(item -> !Boolean.TRUE.equals(item.getUploaded()))
            .map(ProjectMaterialView::getRequirementName)
            .toList();

        BigDecimal completionRate = calculateCompletionRate(requiredTotal, submittedTotal);

        // 完整性是实时派生属性；completion_rate 仅作为由 Service 刷新的展示缓存。
        boolean materialComplete = requiredTotal == 0 || submittedTotal == requiredTotal;
        MaterialCompleteness completeness = materialComplete ? MaterialCompleteness.COMPLETE : MaterialCompleteness.INCOMPLETE;

        // 只更新完成率，不修改项目状态
        project.setCompletionRate(completionRate);
        competitionProjectMapper.updateById(project);

        return ProjectProgressResponse.builder()
            .projectId(projectId)
            .projectName(project.getProjectName())
            .status(project.getStatus()) // 保持原有项目状态不变
            .deadline(project.getDeadline())
            .requiredTotal(requiredTotal)
            .submittedTotal(submittedTotal)
            .missingTotal(missingMaterials.size())
            .completionRate(completionRate)
            .missingMaterials(missingMaterials)
            .materialComplete(materialComplete)
            .completenessLabel(completeness.getLabel())
            .build();
    }

    public ProjectProgressResponse getProgress(Long projectId) {
        // P0-3: 校验当前用户是否有权访问该项目
        checkProjectReadAccess(projectId);
        return refreshProjectProgress(projectId);
    }

    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        // P0-3: 校验当前用户是项目负责人或管理员
        checkProjectLeaderAccess(projectId);
        CompetitionProject project = getProjectOrThrow(projectId);
        requireProjectStatus(project, "修改项目成员", ProjectStatus.DRAFT);
        validateMemberRole(request.getMemberRole());
        if (MEMBER_ROLE_ADVISOR.equals(request.getMemberRole())) {
            validateAdvisorUser(request.getUserId());
            ensureProjectHasNoAdvisor(projectId);
        } else {
            validateUser(request.getUserId());
        }

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
        // P0-3: 校验当前用户是项目负责人或管理员
        checkProjectLeaderAccess(projectId);
        CompetitionProject project = getProjectOrThrow(projectId);
        requireProjectStatus(project, "修改项目成员", ProjectStatus.DRAFT);

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
            throw new BusinessException("没找到项目: projectId=" + projectId);
        }
        return project;
    }

    /**
     * 获取当前用户参与的所有项目列表（含进度和审核汇总）。
     * 适用于教师查看指导的项目、学生查看自己参与的项目。
     */
    public List<ProjectListView> getMyProjects(Long userId) {
        List<ProjectListView> projects = projectMemberMapper.findProjectListByUserId(
            userId, requiredMemberRoleForCurrentUser());
        fillProjectListComputedFields(projects);
        return projects;
    }

    /**
     * 管理员全量项目分页查询，支持关键字、状态和通知筛选。
     * <p>
     * 与 {@link #getMyProjectsPaginated} 不同，本方法不限定当前用户参与的项目，
     * 而是查询平台内所有未删除的项目。仅 ADMIN 角色可调用。
     * </p>
     *
     * @param keyword  项目名 / 负责人 / 通知标题模糊搜索
     * @param status   项目状态筛选（可选）
     * @param noticeId 所属通知 ID 筛选（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<ProjectListView> getAllProjectsPaginated(
        String keyword, String status, Long noticeId, int pageNum, int pageSize
    ) {
        Page<ProjectListView> page = new Page<>(pageNum, pageSize);
        Page<ProjectListView> resultPage = competitionProjectMapper.findAllProjectsPage(
            page, keyword, status, noticeId);
        fillProjectListComputedFields(resultPage.getRecords());
        return PageResult.of(resultPage, resultPage.getRecords());
    }

    /**
     * 分页查询当前用户参与的项目列表，支持关键字和状态筛选。
     * <p>
     * P1-4 新增：替代原有不分页的 getMyProjects，返回带分页元数据的 PageResult。
     * </p>
     *
     * @param userId   当前用户 ID
     * @param keyword  项目名或通知标题模糊搜索
     * @param status   项目状态筛选（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<ProjectListView> getMyProjectsPaginated(
        Long userId, String keyword, String status, LocalDateTime deadlineBefore, int pageNum, int pageSize
    ) {
        Page<ProjectListView> page = new Page<>(pageNum, pageSize);
        Page<ProjectListView> resultPage = projectMemberMapper.findProjectListByUserIdPage(
            page, userId, requiredMemberRoleForCurrentUser(), keyword, status, deadlineBefore);
        fillProjectListComputedFields(resultPage.getRecords());
        return PageResult.of(resultPage, resultPage.getRecords());
    }

    public PageResult<TeacherReviewHistoryView> getMyReviewHistory(int pageNum, int pageSize) {
        String role = SecurityUtils.getCurrentUserRole();
        if (!ROLE_TEACHER.equalsIgnoreCase(role) && !ROLE_ADMIN.equalsIgnoreCase(role)) {
            throw new BusinessException(403, "只有教师或管理员可以查看审核记录");
        }
        Page<TeacherReviewHistoryView> page = reviewRecordMapper.findMyReviewHistory(
            new Page<>(pageNum, pageSize), SecurityUtils.getCurrentUserId(), ROLE_ADMIN.equalsIgnoreCase(role));
        return PageResult.of(page, page.getRecords());
    }

    /**
     * 为项目列表中的每个项目填充计算字段（成员名、提交数、审核数）。
     */
    private void fillProjectListComputedFields(List<ProjectListView> projects) {
        for (ProjectListView project : projects) {
            List<String> memberNames = projectMemberMapper.findMemberViewsByProjectId(project.getProjectId())
                .stream()
                .map(m -> m.getRealName() + "（" + roleLabel(m.getMemberRole()) + "）")
                .toList();
            project.setMemberNames(memberNames);

            List<ProjectMaterialView> materials = findCurrentMaterialViews(project.getProjectId());
            int total = materials.size();
            int submitted = (int) materials.stream()
                .filter(m -> m.getCurrentVersionId() != null)
                .count();
            int reviewed = (int) materials.stream()
                .filter(m -> m.getReviewStatus() != null)
                .count();
            project.setSubmittedCount(submitted);
            project.setTotalCount(total);
            project.setReviewedCount(reviewed);

            List<ProjectMaterialView> requiredMaterials = materials.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getRequiredFlag()))
                .toList();
            int requiredSubmitted = (int) requiredMaterials.stream()
                .filter(m -> Boolean.TRUE.equals(m.getUploaded()))
                .count();
            project.setCompletionRate(calculateCompletionRate(requiredMaterials.size(), requiredSubmitted));
        }
    }

    /**
     * 获取项目各材料的审核状态汇总。
     */
    public List<ProjectMaterialView> getProjectReviewStatus(Long projectId) {
        // P0-3: 校验当前用户是否有权访问该项目
        checkProjectReadAccess(projectId);
        // Ensure project exists
        getProjectOrThrow(projectId);
        return findCurrentMaterialViews(projectId);
    }

    /**
     * 读取 ACTIVE requirement 的当前版本视图，并由 Java 统一派生上传与满足状态。
     * Mapper 只负责取数，不承担完成度算法。
     */
    List<ProjectMaterialView> findCurrentMaterialViews(Long projectId) {
        List<ProjectMaterialView> materials = projectMaterialMapper.findLatestDetailsByProjectId(projectId);
        enrichCurrentMaterialViews(materials);
        return materials;
    }

    void enrichCurrentMaterialViews(List<ProjectMaterialView> materials) {
        materials.forEach(material -> {
            boolean uploaded = material.getCurrentVersionId() != null && material.getFileId() != null;
            material.setUploaded(uploaded);
            material.setRequirementSatisfied(!Integer.valueOf(1).equals(material.getRequiredFlag()) || uploaded);
            material.setSubmitStatus(uploaded ? "submitted" : "pending");
        });
    }

    private BigDecimal calculateCompletionRate(int requiredTotal, int submittedTotal) {
        return requiredTotal == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(submittedTotal * 100.0 / requiredTotal)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void requireProjectStatus(
        CompetitionProject project, String operation, ProjectStatus... allowedStatuses
    ) {
        ProjectStatus current = project.getProjectStatus();
        for (ProjectStatus allowed : allowedStatuses) {
            if (current == allowed) {
                return;
            }
        }
        throw new BusinessException(409, "项目状态为 " + current.name() + " 时不允许" + operation);
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
            saveMember(projectId, request.getAdvisorId(), MEMBER_ROLE_ADVISOR);
        }
        if (!CollectionUtils.isEmpty(request.getMemberUserIds())) {
            memberIds.addAll(request.getMemberUserIds());
        }
        memberIds.remove(request.getLeaderId());
        memberIds.remove(request.getAdvisorId());
        memberIds.forEach(userId -> saveMember(projectId, userId, MEMBER_ROLE_MEMBER));
    }

    private int initializeMaterials(Long projectId, Long noticeId) {
        List<MaterialRequirement> requirements = noticeService.findRequirementsByNoticeId(noticeId);
        List<ProjectMaterial> materials = new ArrayList<>();
        for (MaterialRequirement requirement : requirements) {
            ProjectMaterial projectMaterial = new ProjectMaterial();
            projectMaterial.setProjectId(projectId);
            projectMaterial.setRequirementId(requirement.getRequirementId());
            projectMaterial.setCurrentVersionId(null); // 初始无提交版本
            projectMaterial.setVersionNo(0); // 未上传占位，不占用首个真实文件的 V1
            projectMaterial.setRemark("备注");
            materials.add(projectMaterial);
        }
        // 批量插入
        if (!materials.isEmpty()) {
            projectMaterialMapper.insert(materials, materials.size());
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

    private void validateAdvisorUser(Long userId) {
        SysUser user = getUserOrThrow(userId);
        if (!ROLE_TEACHER.equalsIgnoreCase(user.getRole())) {
            throw new BusinessException("指导教师必须是 teacher 角色用户");
        }
    }

    private void validateMemberRole(String memberRole) {
        if (!MEMBER_ROLE_MEMBER.equals(memberRole) && !MEMBER_ROLE_ADVISOR.equals(memberRole)) {
            throw new BusinessException("成员角色只能为 member 或 advisor");
        }
    }

    private void ensureProjectHasNoAdvisor(Long projectId) {
        boolean hasAdvisor = projectMemberMapper.exists(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getMemberRole, MEMBER_ROLE_ADVISOR)
        );
        if (hasAdvisor) {
            throw new BusinessException("项目已指定指导教师，请先移除原指导教师后再变更");
        }
    }

    private String requiredMemberRoleForCurrentUser() {
        return ROLE_TEACHER.equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
            ? MEMBER_ROLE_ADVISOR
            : null;
    }

    private SysUser getUserOrThrow(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("User not found: userId=" + userId);
        }
        return user;
    }

    // ==================== P0-3: 资源归属校验方法 ====================

    /**
     * 校验当前用户是否有权访问指定项目（公开方法，供其他 Service 调用）。
     * <p>
     * <b>访问规则</b>：项目成员（负责人/指导教师/成员）或 ADMIN 可访问。
     * 其他人访问 → BusinessException("无权访问该项目")。
     * </p>
     *
     * @param projectId 目标项目 ID
     * @throws BusinessException 如果当前用户无权访问该项目
     */
    public void checkProjectAccess(Long projectId) {
        getProjectOrThrow(projectId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentRole = SecurityUtils.getCurrentUserRole();

        // ADMIN 可以查看所有项目
        if (ROLE_ADMIN.equalsIgnoreCase(currentRole)) {
            return;
        }

        // 检查当前用户是否是项目成员
        boolean isMember = projectMemberMapper.exists(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, currentUserId)
        );

        if (!isMember) {
            throw new BusinessException(403, "无权访问该项目：您不是项目成员、指导教师或管理员");
        }
    }

    /**
     * 校验读取项目资源的权限。
     * 学生项目成员、项目 advisor 或 ADMIN 可以读取；普通 TEACHER 即使项目处于
     * UNDER_REVIEW 也不能读取未分配项目。
     */
    public void checkProjectReadAccess(Long projectId) {
        getProjectOrThrow(projectId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentRole = SecurityUtils.getCurrentUserRole();
        if (ROLE_ADMIN.equalsIgnoreCase(currentRole)) {
            return;
        }
        if (ROLE_TEACHER.equalsIgnoreCase(currentRole)) {
            if (hasProjectRole(projectId, currentUserId, MEMBER_ROLE_ADVISOR)) {
                return;
            }
            throw new BusinessException(403, "无权访问该项目：您不是该项目的指导教师");
        }
        checkProjectAccess(projectId);
    }

    /**
     * 校验项目审核权限。只有项目 advisor 或 ADMIN 可以执行材料审核、项目通过和退回。
     */
    public void checkProjectReviewAccess(Long projectId) {
        getProjectOrThrow(projectId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentRole = SecurityUtils.getCurrentUserRole();
        if (ROLE_ADMIN.equalsIgnoreCase(currentRole)) {
            return;
        }
        if (ROLE_TEACHER.equalsIgnoreCase(currentRole)
                && hasProjectRole(projectId, currentUserId, MEMBER_ROLE_ADVISOR)) {
            return;
        }
        throw new BusinessException(403, "只有该项目的指导教师或管理员可以执行审核操作");
    }

    private boolean hasProjectRole(Long projectId, Long userId, String memberRole) {
        return projectMemberMapper.exists(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .eq(ProjectMember::getMemberRole, memberRole)
        );
    }

    /**
     * 校验当前用户是否是项目负责人（或 ADMIN）。
     * <p>
     * 用于添加/移除成员等仅负责人可执行的操作。
     * </p>
     *
     * @param projectId 目标项目 ID
     * @throws BusinessException 如果当前用户不是项目负责人且不是管理员
     */
    private void checkProjectLeaderAccess(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentRole = SecurityUtils.getCurrentUserRole();

        // ADMIN 可以管理所有项目
        if ("admin".equalsIgnoreCase(currentRole)) {
            return;
        }

        CompetitionProject project = getProjectOrThrow(projectId);
        if (!project.getLeaderId().equals(currentUserId)) {
            throw new BusinessException("只有项目负责人或管理员可以执行此操作");
        }
    }

    // ==================== P1-2: 项目状态流转方法 ====================

    /**
     * 首次提交人工审核 —— DRAFT → UNDER_REVIEW。
     * REVISION_REQUIRED 项目必须使用 {@link #resubmit(Long)}。
     * <p>提交前检查：当前状态允许提交、通知已发布、未超截止时间、所有必交材料都有当前有效版本、</p>
     * <p>当前用户是项目负责人、没有正在执行的冲突操作。</p>
     */
    @Transactional
    public void submitForReview(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        CompetitionProject project = getProjectOrThrow(projectId);

        if (project.getProjectStatus() != ProjectStatus.DRAFT) {
            throw new com.eliza.aicompetition.exception.InvalidStatusTransitionException(
                "ProjectStatus.submit", project.getProjectStatus(), ProjectStatus.UNDER_REVIEW);
        }

        // 检查当前用户是项目负责人或管理员
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                && !project.getLeaderId().equals(currentUserId)) {
            throw new BusinessException("只有项目负责人或管理员可以提交审核");
        }

        // 检查通知已发布
        CompetitionNotice notice = noticeService.getNoticeOrThrow(project.getNoticeId());
        if (!NoticePublishStatus.PUBLISHED.name().equals(notice.getPublishStatus())) {
            throw new BusinessException("通知尚未发布，无法提交审核");
        }

        // 检查未超过截止时间
        if (project.getDeadline() != null && project.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("已超过申报截止时间，无法提交审核");
        }

        // 检查所有必交材料都有当前有效版本
        ProjectProgressResponse progress = refreshProjectProgress(projectId);
        if (!Boolean.TRUE.equals(progress.getMaterialComplete())) {
            throw new BusinessException("材料未齐全，无法提交审核。缺失材料：" + String.join("、", progress.getMissingMaterials()));
        }

        ProjectStatus previousStatus = project.getProjectStatus();
        // 使用领域方法提交
        project.submitForReview();
        int updated = competitionProjectMapper.updateById(project);
        if (updated == 0) {
            throw new BusinessException(409, "项目状态已被其他操作修改，请刷新后重试");
        }
        appendProjectEvent(projectId, "lifecycle", "UNDER_REVIEW", "项目首次提交审核");
        log.info("项目已提交人工审核: projectId={}, from={}, to={}",
            projectId, previousStatus.name(), ProjectStatus.UNDER_REVIEW.name());
    }

    /**
     * 教师退回修改 —— UNDER_REVIEW → REVISION_REQUIRED。
     * <p>只有教师或管理员可以退回。使用乐观锁防止并发冲突。</p>
     */
    @Transactional
    public void requestRevision(Long projectId, String reason) {
        checkProjectReviewAccess(projectId);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(400, "退回项目必须填写具体原因");
        }
        CompetitionProject project = getProjectOrThrow(projectId);

        // 使用领域方法退回
        project.requestRevision();
        int updated = competitionProjectMapper.updateById(project);
        if (updated == 0) {
            throw new BusinessException(409, "项目状态已被其他操作修改，请刷新后重试");
        }

        // 通知项目负责人
        String safeReason = reason.trim();
        appendProjectReviewRecord(projectId, "REVISION_REQUIRED", safeReason);
        NotifyMessage msg = new NotifyMessage();
        msg.setProjectId(projectId);
        msg.setReceiverId(project.getLeaderId());
        msg.setMsgType("project");
        msg.setMsgContent("您的项目「" + project.getProjectName() + "」已被退回修改。审核意见：" + safeReason);
        msg.setIsRead(0);
        notifyMessageMapper.insert(msg);

        log.info("项目已被退回修改: projectId={}, reason={}, userId={}",
            projectId, reason, SecurityUtils.getCurrentUserId());
    }

    /**
     * 教师审核通过 —— UNDER_REVIEW → APPROVED。
     * <p>只有教师或管理员可以通过。使用乐观锁防止并发冲突。</p>
     * <p><b>业务校验</b>：所有必交材料均已提交且最新审核决定均为 APPROVED，否则返回 409。</p>
     */
    @Transactional
    public void approve(Long projectId) {
        checkProjectReviewAccess(projectId);
        CompetitionProject project = getProjectOrThrow(projectId);

        // 使用领域方法通过（内部校验 UNDER_REVIEW → APPROVED）
        project.approve();

        // 业务校验：所有必交材料均已提交且最新审核决定均为 APPROVED
        List<ProjectMaterialView> materials = findCurrentMaterialViews(projectId);
        List<ProjectMaterialView> requiredMaterials = materials.stream()
            .filter(m -> Integer.valueOf(1).equals(m.getRequiredFlag()))
            .toList();

        List<String> unsubmitted = requiredMaterials.stream()
            .filter(m -> m.getCurrentVersionId() == null)
            .map(ProjectMaterialView::getRequirementName)
            .toList();
        if (!unsubmitted.isEmpty()) {
            throw new BusinessException(409, "仍有必交材料未提交，不能通过项目：" + String.join("、", unsubmitted));
        }

        List<String> unreviewedOrRejected = requiredMaterials.stream()
            .filter(m -> !"APPROVED".equals(m.getReviewStatus()))
            .map(m -> m.getRequirementName() + "（" + (m.getReviewStatus() == null ? "未审核" : "需修改") + "）")
            .toList();
        if (!unreviewedOrRejected.isEmpty()) {
            throw new BusinessException(409, "仍有必交材料未审核或需要修改，不能通过项目：" + String.join("、", unreviewedOrRejected));
        }

        int updated = competitionProjectMapper.updateById(project);
        if (updated == 0) {
            throw new BusinessException(409, "项目状态已被其他操作修改，请刷新后重试");
        }

        appendProjectReviewRecord(projectId, "APPROVED", "项目审核通过");

        // 通知项目负责人
        NotifyMessage msg = new NotifyMessage();
        msg.setProjectId(projectId);
        msg.setReceiverId(project.getLeaderId());
        msg.setMsgType("project");
        msg.setMsgContent("您的项目「" + project.getProjectName() + "」已审核通过！");
        msg.setIsRead(0);
        notifyMessageMapper.insert(msg);

        log.info("项目已审核通过: projectId={}, userId={}",
            projectId, SecurityUtils.getCurrentUserId());
    }

    /**
     * 学生重新提交 —— REVISION_REQUIRED → UNDER_REVIEW。
     * <p>退回修改后，学生修改材料后重新提交审核。</p>
     */
    @Transactional
    public void resubmit(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        CompetitionProject project = getProjectOrThrow(projectId);

        requireProjectStatus(project, "重新提交", ProjectStatus.REVISION_REQUIRED);

        // 检查当前用户是项目负责人
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                && !project.getLeaderId().equals(currentUserId)) {
            throw new BusinessException("只有项目负责人可以重新提交审核");
        }

        if (project.getDeadline() != null && project.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("已超过申报截止时间，无法重新提交审核");
        }

        // 检查材料完整性
        ProjectProgressResponse progress = refreshProjectProgress(projectId);
        if (Boolean.TRUE.equals(progress.getMaterialComplete())) {
            // ok
        } else {
            throw new BusinessException("材料未齐全，无法重新提交。缺失材料：" + String.join("、", progress.getMissingMaterials()));
        }

        // 检查通知仍为已发布
        CompetitionNotice notice = noticeService.getNoticeOrThrow(project.getNoticeId());
        if (!NoticePublishStatus.PUBLISHED.name().equals(notice.getPublishStatus())) {
            throw new BusinessException("通知已归档或未发布，无法重新提交");
        }

        // 使用领域方法重新提交
        project.resubmit();
        int updated = competitionProjectMapper.updateById(project);
        if (updated == 0) {
            throw new BusinessException(409, "项目状态已被其他操作修改，请刷新后重试");
        }
        appendProjectEvent(projectId, "lifecycle", "UNDER_REVIEW", "项目修改后重新提交审核");
        log.info("项目已重新提交审核: projectId={}, userId={}", projectId, currentUserId);
    }

    private void appendProjectReviewRecord(Long projectId, String result, String comment) {
        appendProjectEvent(projectId, "teacher_project", result, comment);
    }

    private void appendProjectEvent(Long projectId, String type, String result, String comment) {
        com.eliza.aicompetition.entity.ReviewRecord record = new com.eliza.aicompetition.entity.ReviewRecord();
        record.setProjectId(projectId);
        record.setReviewerId(SecurityUtils.getCurrentUserId());
        record.setReviewType(type);
        record.setReviewResult(result);
        record.setReviewComment(comment);
        record.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(record);
    }
}
