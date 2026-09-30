package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * File download controller.
 * Returns original file bytes so the browser can display or download the file natively.
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private final FileAssetMapper fileAssetMapper;
    private final ProjectMaterialMapper projectMaterialMapper;
    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final ProjectService projectService;

    public FileController(FileAssetMapper fileAssetMapper,
                          ProjectMaterialMapper projectMaterialMapper,
                          CompetitionNoticeMapper competitionNoticeMapper,
                          ProjectService projectService) {
        this.fileAssetMapper = fileAssetMapper;
        this.projectMaterialMapper = projectMaterialMapper;
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.projectService = projectService;
    }

    /**
     * Download the original file binary content.
     * Browser will render PDF inline, and prompt download for DOCX / XLSX.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
        FileAsset fileAsset = fileAssetMapper.selectById(fileId);
        if (fileAsset == null) {
            throw new BusinessException(404, "文件不存在: fileId=" + fileId);
        }

        ProjectMaterial material = projectMaterialMapper.selectOne(
            new LambdaQueryWrapper<ProjectMaterial>()
                .eq(ProjectMaterial::getFileId, fileId)
                .last("LIMIT 1"));
        if (material != null) {
            projectService.checkProjectReadAccess(material.getProjectId());
        } else {
            CompetitionNotice notice = competitionNoticeMapper.selectOne(
                new LambdaQueryWrapper<CompetitionNotice>()
                    .eq(CompetitionNotice::getNoticeFileId, fileId)
                    .last("LIMIT 1"));
            if (notice == null) {
                throw new BusinessException(404, "文件关联资源不存在: fileId=" + fileId);
            }
            if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                    && !"PUBLISHED".equalsIgnoreCase(notice.getPublishStatus())) {
                throw new BusinessException(404, "文件不存在");
            }
        }

        byte[] fileBytes = fileAsset.getFileBlob();
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(404, "文件内容为空: fileId=" + fileId);
        }

        MediaType mediaType = resolveMediaType(fileAsset.getFileExt());

        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(fileBytes.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline()
                    .filename(fileAsset.getFileName())
                    .build()
                    .toString())
            .body(fileBytes);
    }

    private MediaType resolveMediaType(String ext) {
        if (ext == null) return MediaType.APPLICATION_OCTET_STREAM;
        return switch (ext.toLowerCase()) {
            case "pdf"  -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc"  -> MediaType.valueOf("application/msword");
            case "xlsx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls"  -> MediaType.valueOf("application/vnd.ms-excel");
            case "txt"  -> MediaType.TEXT_PLAIN;
            case "png"  -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default     -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
