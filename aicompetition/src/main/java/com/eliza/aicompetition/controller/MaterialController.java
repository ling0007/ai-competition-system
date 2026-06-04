package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.service.MaterialService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/material")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MaterialUploadResponse> uploadMaterial(
        @RequestParam("projectId") Long projectId,
        @RequestParam("requirementId") Long requirementId,
        @RequestParam(value = "uploadedBy", defaultValue = "3") Long uploadedBy,
        @RequestParam(value = "remark", required = false) String remark,
        @RequestParam("file") MultipartFile file
    ) {
        MaterialUploadResponse response = materialService.uploadMaterial(projectId, requirementId, uploadedBy, remark, file);
        return ApiResponse.success("材料上传成功", response);
    }
}
