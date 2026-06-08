package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.eliza.aicompetition.dto.material.MaterialReviewRequest;
import com.eliza.aicompetition.dto.material.MaterialReviewResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.entity.ProjectMember;
import com.eliza.aicompetition.entity.ReviewRecord;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MaterialService {

    private final FileAssetMapper fileAssetMapper;
    private final ProjectMaterialMapper projectMaterialMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final ProjectService projectService;
    private final NoticeService noticeService;

    public MaterialService(
        FileAssetMapper fileAssetMapper,
        ProjectMaterialMapper projectMaterialMapper,
        SysUserMapper sysUserMapper,
        ProjectMemberMapper projectMemberMapper,
        ReviewRecordMapper reviewRecordMapper,
        NotifyMessageMapper notifyMessageMapper,
        ProjectService projectService,
        NoticeService noticeService
    ) {
        this.fileAssetMapper = fileAssetMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.sysUserMapper = sysUserMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.notifyMessageMapper = notifyMessageMapper;
        this.projectService = projectService;
        this.noticeService = noticeService;
    }

    @Transactional
    public MaterialUploadResponse uploadMaterial(
        Long projectId,
        Long requirementId,
        Long uploadedBy,
        String remark,
        MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Please upload a material file.");
        }
        validateUser(uploadedBy);

        CompetitionProject project = projectService.getProjectOrThrow(projectId);
        MaterialRequirement requirement = findRequirement(project.getNoticeId(), requirementId);

        FileAsset fileAsset = buildFileAsset(file, uploadedBy);
        fileAssetMapper.insert(fileAsset);
        Long fileId = fileAsset.getFileId();
        LocalDateTime submittedAt = LocalDateTime.now();

        ProjectMaterial latestMaterial = findLatestProjectMaterial(projectId, requirementId);

        Long materialId;
        Integer versionNo;
        if (latestMaterial != null
            && ("pending".equalsIgnoreCase(latestMaterial.getSubmitStatus()) || "rejected".equalsIgnoreCase(latestMaterial.getSubmitStatus()))
            && latestMaterial.getFileId() == null) {
            latestMaterial.setFileId(fileId);
            latestMaterial.setSubmitStatus("submitted");
            latestMaterial.setRemark(buildRemark(remark, requirement));
            latestMaterial.setSubmittedAt(submittedAt);
            projectMaterialMapper.updateById(latestMaterial);
            materialId = latestMaterial.getMaterialId();
            versionNo = latestMaterial.getVersionNo();
        } else {
            ProjectMaterial newMaterial = new ProjectMaterial();
            newMaterial.setProjectId(projectId);
            newMaterial.setRequirementId(requirementId);
            newMaterial.setFileId(fileId);
            newMaterial.setSubmitStatus("submitted");
            newMaterial.setVersionNo(latestMaterial == null ? 1 : latestMaterial.getVersionNo() + 1);
            newMaterial.setRemark(buildRemark(remark, requirement));
            newMaterial.setSubmittedAt(submittedAt);
            projectMaterialMapper.insert(newMaterial);
            materialId = newMaterial.getMaterialId();
            versionNo = newMaterial.getVersionNo();
        }

        ProjectProgressResponse progress = projectService.refreshProjectProgress(projectId);
        return new MaterialUploadResponse(
            materialId,
            projectId,
            requirementId,
            fileId,
            versionNo,
            "submitted",
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
     */
    @Transactional
    public MaterialReviewResponse reviewMaterial(MaterialReviewRequest request) {
        // Validate reviewer exists
        SysUser reviewer = sysUserMapper.selectById(request.getReviewerId());
        if (reviewer == null) {
            throw new BusinessException("审核人不存在: reviewerId=" + request.getReviewerId());
        }

        // Validate review status
        if (!"approved".equals(request.getReviewStatus()) && !"revision".equals(request.getReviewStatus())) {
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

        // Verify reviewer is a teacher member (advisor or teacher-role member) of this project
        boolean isReviewerInProject = projectMemberMapper.selectList(
            new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, request.getProjectId())
                .eq(ProjectMember::getUserId, request.getReviewerId())
        ).stream().anyMatch(m -> "advisor".equals(m.getMemberRole()));

        if (!isReviewerInProject && !"teacher".equals(reviewer.getRole()) && !"admin".equals(reviewer.getRole())) {
            throw new BusinessException("只有项目指导教师或管理员可以审核材料");
        }

        // Update material review fields
        material.setReviewStatus(request.getReviewStatus());
        material.setReviewComment(request.getReviewComment());
        material.setReviewedBy(request.getReviewerId());
        material.setReviewedAt(java.time.LocalDateTime.now());
        projectMaterialMapper.updateById(material);

        // Create review record for audit trail
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setProjectId(request.getProjectId());
        reviewRecord.setReviewerId(request.getReviewerId());
        reviewRecord.setReviewType("teacher");
        reviewRecord.setReviewResult(request.getReviewStatus());
        reviewRecord.setReviewComment(
            "材料「" + findRequirementName(material.getRequirementId(), request.getProjectId()) + "」审核结果："
            + ("approved".equals(request.getReviewStatus()) ? "通过" : "需修改")
            + (request.getReviewComment() != null && !request.getReviewComment().isBlank()
                ? " —— " + request.getReviewComment() : "")
        );
        reviewRecordMapper.insert(reviewRecord);

        // If revision requested, notify project leader
        if ("revision".equals(request.getReviewStatus())) {
            CompetitionProject project = projectService.getProjectOrThrow(request.getProjectId());
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
            request.getReviewStatus(),
            request.getReviewComment(),
            material.getReviewedAt()
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

    /**
     * 重置材料审核状态 —— 清空审核结果，恢复为未审核。
     * 使用 UpdateWrapper 强制写入 null，绕过 MyBatis-Plus 默认忽略 null 字段的策略。
     */
    @Transactional
    public void resetMaterialReview(Long materialId) {
        ProjectMaterial material = projectMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("材料记录不存在: materialId=" + materialId);
        }
        UpdateWrapper<ProjectMaterial> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("material_id", materialId)
            .set("review_status", null)
            .set("review_comment", null)
            .set("reviewed_by", null)
            .set("reviewed_at", null);
        projectMaterialMapper.update(null, updateWrapper);
    }
}
