package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.common.enums.MaterialReviewDecision;
import com.eliza.aicompetition.common.enums.ProjectStatus;
import com.eliza.aicompetition.entity.MaterialReview;
import com.eliza.aicompetition.dto.material.MaterialReviewRequest;
import com.eliza.aicompetition.dto.material.MaterialReviewResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.dto.material.MaterialVersionView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.entity.ProjectMember;
import com.eliza.aicompetition.entity.ReviewRecord;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.MaterialReviewMapper;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;

/**
 * 材料管理 Service。
 * <p>
 * <b>权限校验（P0-3）</b>：
 * <ul>
 *   <li>上传材料 → 必须是项目成员</li>
 *   <li>审核材料 → 必须是项目 advisor 或管理员</li>
 * </ul>
 * </p>
 */
@Service
public class MaterialService {

    private static final long MAX_MATERIAL_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_MATERIAL_EXTENSIONS = Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "png", "jpg", "jpeg", "txt"
    );

    private final FileAssetMapper fileAssetMapper;
    private final ProjectMaterialMapper projectMaterialMapper;
    private final CompetitionProjectMapper competitionProjectMapper;
    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final MaterialReviewMapper materialReviewMapper;
    private final ProjectService projectService;
    private final NoticeService noticeService;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;
    private final Duration uploadLockTtl;

    public MaterialService(
        FileAssetMapper fileAssetMapper,
        ProjectMaterialMapper projectMaterialMapper,
        CompetitionProjectMapper competitionProjectMapper,
        CompetitionNoticeMapper competitionNoticeMapper,
        SysUserMapper sysUserMapper,
        ProjectMemberMapper projectMemberMapper,
        ReviewRecordMapper reviewRecordMapper,
        NotifyMessageMapper notifyMessageMapper,
        MaterialReviewMapper materialReviewMapper,
        ProjectService projectService,
        NoticeService noticeService,
        RedisLockService redisLockService,
        TransactionTemplate transactionTemplate,
        @Value("${material.upload.lock-ttl-seconds:120}") long uploadLockTtlSeconds
    ) {
        this.fileAssetMapper = fileAssetMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.competitionProjectMapper = competitionProjectMapper;
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.sysUserMapper = sysUserMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.notifyMessageMapper = notifyMessageMapper;
        this.materialReviewMapper = materialReviewMapper;
        this.projectService = projectService;
        this.noticeService = noticeService;
        this.redisLockService = redisLockService;
        this.transactionTemplate = transactionTemplate;
        this.uploadLockTtl = Duration.ofSeconds(uploadLockTtlSeconds);
    }

    public MaterialUploadResponse uploadMaterial(
        Long projectId,
        Long requirementId,
        Long uploadedBy,
        String remark,
        MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传非空材料文件");
        }
        validateMaterialFile(file);
        String currentRole = SecurityUtils.getCurrentUserRole();
        if (!"student".equalsIgnoreCase(currentRole) && !"admin".equalsIgnoreCase(currentRole)) {
            throw new BusinessException(403, "只有学生或管理员可以上传材料");
        }
        validateUser(uploadedBy);

        // P0-3: 校验当前用户是否是项目成员（防止非成员上传材料）
        if (!isProjectMember(projectId, uploadedBy)) {
            throw new BusinessException("只有项目成员可以上传材料");
        }

        // The Redis lease only reduces contention; project-row locking and the version unique key
        // provide correctness when Redis is unavailable or the lease expires.
        String lockKey = "material:upload:" + projectId + ":" + requirementId;
        RedisLockService.Lease lease = redisLockService.acquire(lockKey, uploadLockTtl);
        if (!lease.allowed()) {
            throw new BusinessException(409, "该材料正在提交中，请勿重复操作");
        }
        try {
            // execute returns only after the DB commit or rollback completes.
            return transactionTemplate.execute(tx ->
                doUploadMaterial(projectId, requirementId, uploadedBy, remark, file));
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "材料版本已变化，请刷新后重试上传");
        } finally {
            // If a caller already owns a wider transaction, defer release to its actual completion.
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        redisLockService.release(lockKey, lease);
                    }
                });
            } else {
                redisLockService.release(lockKey, lease);
            }
        }
    }

    public List<MaterialVersionView> getVersionHistory(Long projectId, Long requirementId) {
        projectService.checkProjectReadAccess(projectId);
        CompetitionProject project = projectService.getProjectOrThrow(projectId);
        findRequirement(project.getNoticeId(), requirementId);
        return projectMaterialMapper.findVersionHistory(projectId, requirementId);
    }

    private void validateMaterialFile(MultipartFile file) {
        if (file.getSize() > MAX_MATERIAL_FILE_SIZE) {
            throw new BusinessException("材料文件不能超过 50MB");
        }
        String rawExtension = getExtension(file.getOriginalFilename());
        String extension = rawExtension == null ? "" : rawExtension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_MATERIAL_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的材料文件类型，请上传 PDF、Office、TXT、ZIP 或常见图片文件");
        }
    }

    /**
     * 执行材料上传（由 {@link #uploadMaterial} 在 Redis 锁保护下调用）。
     */
    private MaterialUploadResponse doUploadMaterial(
        Long projectId,
        Long requirementId,
        Long uploadedBy,
        String remark,
        MultipartFile file
    ) {

        // Serialize uploads with AI commits before changing a current-version row.
        CompetitionProject project = competitionProjectMapper.selectOne(
            new LambdaQueryWrapper<CompetitionProject>()
                .eq(CompetitionProject::getProjectId, projectId).last("FOR UPDATE"));
        if (project == null) throw new BusinessException(404, "项目不存在");
        // Requirement confirmation updates the notice before replacing the ACTIVE set.
        CompetitionNotice notice = competitionNoticeMapper.selectOne(new LambdaQueryWrapper<CompetitionNotice>()
            .eq(CompetitionNotice::getNoticeId, project.getNoticeId()).last("FOR UPDATE"));
        if (notice == null) throw new BusinessException(404, "通知不存在");
        ProjectStatus projectStatus = project.getProjectStatus();
        if (projectStatus != ProjectStatus.DRAFT && projectStatus != ProjectStatus.REVISION_REQUIRED) {
            throw new BusinessException(409, "当前项目状态不允许上传或替换材料");
        }
        MaterialRequirement requirement = findRequirement(project.getNoticeId(), requirementId);
        if (!"ACTIVE".equalsIgnoreCase(requirement.getStatus())) {
            throw new BusinessException(409, "材料要求已停用，无法上传");
        }

        FileAsset fileAsset = buildFileAsset(file, uploadedBy);
        fileAssetMapper.insert(fileAsset);
        Long fileId = fileAsset.getFileId();
        LocalDateTime submittedAt = LocalDateTime.now();

        ProjectMaterial latestMaterial = findLatestProjectMaterial(projectId, requirementId);

        // 兼容旧项目：原先的 V1 无文件占位占用了唯一键，先降为 V0 再插入真正 V1。
        if (latestMaterial != null && latestMaterial.getFileId() == null
                && latestMaterial.getVersionNo() == 1) {
            latestMaterial.setVersionNo(0);
            projectMaterialMapper.updateById(latestMaterial);
        }

        // 始终创建新版本，不覆盖旧版本
        ProjectMaterial newMaterial = new ProjectMaterial();
        newMaterial.setProjectId(projectId);
        newMaterial.setRequirementId(requirementId);
        newMaterial.setFileId(fileId);
        // 建项目时的无文件占位行不算上传版本；第一次真实上传应从 V1 开始。
        newMaterial.setVersionNo(latestMaterial == null || latestMaterial.getFileId() == null
            ? 1 : latestMaterial.getVersionNo() + 1);
        newMaterial.setRemark(buildRemark(remark, requirement));
        newMaterial.setSubmittedAt(submittedAt);
        newMaterial.setCurrentVersionId(null); // 先插入，后面更新
        projectMaterialMapper.insert(newMaterial);
        Long materialId = newMaterial.getMaterialId();
        Integer versionNo = newMaterial.getVersionNo();

        // 更新当前版本：将同一 project+requirement 下所有旧版本的 current_version_id 清空
        // 然后设置新版本为当前版本
        updateCurrentVersion(projectId, requirementId, materialId);
        newMaterial.setCurrentVersionId(materialId);

        ProjectProgressResponse progress = projectService.refreshProjectProgress(projectId);
        return new MaterialUploadResponse(
            materialId,
            projectId,
            requirementId,
            fileId,
            versionNo,
            newMaterial.isSubmitted() ? "submitted" : "pending",
            progress.getStatus(),
            progress.getCompletionRate()
        );
    }

    private MaterialRequirement findRequirement(Long noticeId, Long requirementId) {
        List<MaterialRequirement> requirements = noticeService.findRequirementsByNoticeId(noticeId);
        return requirements.stream()
            .filter(item -> requirementId.equals(item.getRequirementId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException("Requirement not found: requirementId=" + requirementId));
    }

    /**
     * 更新材料当前版本：将同一 project+requirement 下所有旧记录的 current_version_id 清空，
     * 然后设置指定版本为当前有效版本。
     */
    private void updateCurrentVersion(Long projectId, Long requirementId, Long newVersionId) {
        // 清空所有旧版本的 current_version_id
        UpdateWrapper<ProjectMaterial> clearWrapper = new UpdateWrapper<>();
        clearWrapper.eq("project_id", projectId)
            .eq("requirement_id", requirementId)
            .set("current_version_id", null);
        projectMaterialMapper.update(null, clearWrapper);

        // 设置新版本为当前版本
        ProjectMaterial newVersion = projectMaterialMapper.selectById(newVersionId);
        if (newVersion != null) {
            newVersion.setCurrentVersionId(newVersionId);
            projectMaterialMapper.updateById(newVersion);
        }
    }

    private ProjectMaterial findLatestProjectMaterial(Long projectId, Long requirementId) {
        LambdaQueryWrapper<ProjectMaterial> queryWrapper = new LambdaQueryWrapper<ProjectMaterial>()
            .eq(ProjectMaterial::getProjectId, projectId)
            .eq(ProjectMaterial::getRequirementId, requirementId)
            .orderByDesc(ProjectMaterial::getVersionNo)
            .last("limit 1");
        List<ProjectMaterial> materials = projectMaterialMapper.selectList(queryWrapper);
        return materials.isEmpty() ? null : materials.get(0);
    }

    private void validateUser(Long userId) {
        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException("User not found: userId=" + userId);
        }
    }

    private FileAsset buildFileAsset(MultipartFile file, Long uploadedBy) {
        try {
            FileAsset fileAsset = new FileAsset();
            fileAsset.setBizType("material");
            fileAsset.setFileName(file.getOriginalFilename());
            fileAsset.setFileExt(getExtension(file.getOriginalFilename()));
            fileAsset.setFileSize(file.getSize());
            fileAsset.setStoragePath("material/" + LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID());
            fileAsset.setFileBlob(file.getBytes());
            fileAsset.setUploadedBy(uploadedBy);
            return fileAsset;
        } catch (IOException exception) {
            throw new BusinessException("Failed to read material file: " + exception.getMessage());
        }
    }

    private String buildRemark(String remark, MaterialRequirement requirement) {
        if (StringUtils.hasText(remark)) {
            return remark.trim();
        }
        return "Uploaded material: " + requirement.getRequirementName();
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * 教师审核材料 —— 通过(approved)或留下修改意见(revision)。
     * 同时写入 review_record 用于审计，并在退回时通知项目负责人。
     *
     * @param reviewerId 审核人 ID（从 SecurityUtils.getCurrentUserId() 获取，不由请求体传递）
     * @param request    审核请求（projectId, materialId, reviewStatus, reviewComment）
     */
    @Transactional
    public MaterialReviewResponse reviewMaterial(Long reviewerId, MaterialReviewRequest request) {
        // 第一层校验真实用户角色；资源级 advisor 归属由 checkProjectReviewAccess 统一校验。
        SysUser reviewer = sysUserMapper.selectById(reviewerId);
        if (reviewer == null) {
            throw new BusinessException("审核人不存在: reviewerId=" + reviewerId);
        }
        if (!"teacher".equalsIgnoreCase(reviewer.getRole()) && !"admin".equalsIgnoreCase(reviewer.getRole())) {
            throw new BusinessException("只有教师或管理员可以审核材料");
        }

        projectService.checkProjectReviewAccess(request.getProjectId());
        CompetitionProject project = projectService.getProjectOrThrow(request.getProjectId());
        if (project.getProjectStatus() != ProjectStatus.UNDER_REVIEW) {
            throw new BusinessException(409, "只有人工审核中的项目可以审核材料");
        }

        // 解析审核决定
        MaterialReviewDecision decision;
        try {
            decision = MaterialReviewDecision.fromValue(request.getReviewStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("审核状态必须为 approved 或 revision");
        }

        // Validate material exists
        ProjectMaterial material = projectMaterialMapper.selectById(request.getMaterialId());
        if (material == null) {
            throw new BusinessException("材料记录不存在: materialId=" + request.getMaterialId());
        }
        if (!material.getProjectId().equals(request.getProjectId())) {
            throw new BusinessException("材料不属于指定项目");
        }

        // 获取当前有效版本（审核必须针对已提交的版本）
        Long versionId = material.getCurrentVersionId();
        if (versionId == null) {
            throw new BusinessException("该材料尚未提交，无法审核");
        }

        // 创建材料审核记录（新增记录，不覆盖旧数据）
        MaterialReview review = new MaterialReview();
        review.setMaterialId(material.getMaterialId());
        review.setMaterialVersionId(versionId);
        review.setReviewerId(reviewerId);
        review.setDecision(decision.name());
        review.setComment(request.getReviewComment());
        review.setCreatedAt(LocalDateTime.now()); // 手动设值（无 MetaObjectHandler）
        materialReviewMapper.insert(review);

        // 创建项目审核记录用于审计
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setProjectId(request.getProjectId());
        reviewRecord.setReviewerId(reviewerId);
        reviewRecord.setReviewType("teacher");
        reviewRecord.setReviewResult(decision.name());
        reviewRecord.setReviewComment(
            "材料「" + findRequirementName(material.getRequirementId(), request.getProjectId()) + "」审核结果："
            + decision.getLabel()
            + (request.getReviewComment() != null && !request.getReviewComment().isBlank()
                ? " —— " + request.getReviewComment() : "")
        );
        reviewRecordMapper.insert(reviewRecord);

        // 如果退回修改，通知项目负责人
        if (decision == MaterialReviewDecision.REVISION_REQUIRED) {
            NotifyMessage notifyMessage = new NotifyMessage();
            notifyMessage.setProjectId(request.getProjectId());
            notifyMessage.setReceiverId(project.getLeaderId());
            notifyMessage.setMsgType("material");
            notifyMessage.setMsgContent(
                "材料「" + findRequirementName(material.getRequirementId(), request.getProjectId()) + "」"
                + "已被教师退回修改"
                + (request.getReviewComment() != null && !request.getReviewComment().isBlank()
                    ? "，修改意见：" + request.getReviewComment() : "")
            );
            notifyMessage.setIsRead(0);
            notifyMessageMapper.insert(notifyMessage);
        }

        return new MaterialReviewResponse(
            material.getMaterialId(),
            decision.name(),
            request.getReviewComment(),
            review.getCreatedAt()
        );
    }

    private String findRequirementName(Long requirementId, Long projectId) {
        CompetitionProject project = projectService.getProjectOrThrow(projectId);
        return noticeService.findRequirementsByNoticeId(project.getNoticeId()).stream()
            .filter(r -> requirementId.equals(r.getRequirementId()))
            .findFirst()
            .map(com.eliza.aicompetition.entity.MaterialRequirement::getRequirementName)
            .orElse("未知材料");
    }

    // ==================== P1-4: 材料列表分页查询 ====================

    /**
     * 分页查询材料列表，支持按项目和提交状态筛选。
     *
     * @param projectId 项目 ID（可选，为空则查所有项目）
     * @param submitted 提交状态筛选（null=全部, true=已提交, false=未提交）
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页结果
     */
    public PageResult<com.eliza.aicompetition.dto.project.ProjectMaterialView> listMaterials(
        Long projectId, Boolean submitted, int pageNum, int pageSize
    ) {
        if (projectId == null) {
            if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
                throw new BusinessException(403, "只有管理员可以查询全部材料");
            }
        } else {
            projectService.checkProjectReadAccess(projectId);
        }
        Page<com.eliza.aicompetition.dto.project.ProjectMaterialView> page = new Page<>(pageNum, pageSize);
        Page<com.eliza.aicompetition.dto.project.ProjectMaterialView> resultPage =
            projectMaterialMapper.findMaterialsPage(page, projectId, submitted);
        projectService.enrichCurrentMaterialViews(resultPage.getRecords());
        return PageResult.of(resultPage, resultPage.getRecords());
    }

    /**
     * 重置材料审核状态 —— 清空审核结果，恢复为未审核。
     * 使用 UpdateWrapper 强制写入 null，绕过 MyBatis-Plus 默认忽略 null 字段的策略。
     *
     * @deprecated 上传新版本材料会自动创建新记录，旧审核记录保留在 material_review 表中。
     *             新版本自然处于未审核状态，不再需要手动重置。
     */
    @Deprecated
    @Transactional
    public void resetMaterialReview(Long materialId) {
        ProjectMaterial material = projectMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("材料记录不存在: materialId=" + materialId);
        }
        projectService.checkProjectReviewAccess(material.getProjectId());
        UpdateWrapper<ProjectMaterial> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("material_id", materialId)
            .set("review_status", null)
            .set("review_comment", null)
            .set("reviewed_by", null)
            .set("reviewed_at", null);
        projectMaterialMapper.update(null, updateWrapper);
    }

    // ==================== P0-3: 资源归属校验方法 ====================

    /**
     * 检查用户是否是指定项目的成员。
     * <p>
     * ADMIN 角色自动获得所有项目的访问权限（模拟项目成员身份）。
     * </p>
     *
     * @param projectId 项目 ID
     * @param userId    用户 ID
     * @return true 如果用户是项目成员或管理员
     */
    private boolean isProjectMember(Long projectId, Long userId) {
        // ADMIN 拥有所有项目的访问权
        String currentRole = SecurityUtils.getCurrentUserRole();
        if ("admin".equalsIgnoreCase(currentRole)) {
            return true;
        }
        // 检查 project_member 表中是否存在该用户
        return !projectMemberMapper.selectList(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
        ).isEmpty();
    }
}
