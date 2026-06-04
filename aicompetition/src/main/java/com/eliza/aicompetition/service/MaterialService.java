package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
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
    private final ProjectService projectService;
    private final NoticeService noticeService;

    public MaterialService(
        FileAssetMapper fileAssetMapper,
        ProjectMaterialMapper projectMaterialMapper,
        SysUserMapper sysUserMapper,
        ProjectService projectService,
        NoticeService noticeService
    ) {
        this.fileAssetMapper = fileAssetMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.sysUserMapper = sysUserMapper;
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
}
