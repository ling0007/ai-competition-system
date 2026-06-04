package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.FileAssetMapper;
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

    public FileController(FileAssetMapper fileAssetMapper) {
        this.fileAssetMapper = fileAssetMapper;
    }

    /**
     * Download the original file binary content.
     * Browser will render PDF inline, and prompt download for DOCX / XLSX.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
        FileAsset fileAsset = fileAssetMapper.selectById(fileId);
        if (fileAsset == null) {
            throw new BusinessException("文件不存在: fileId=" + fileId);
        }

        byte[] fileBytes = fileAsset.getFileBlob();
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException("文件内容为空: fileId=" + fileId);
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
