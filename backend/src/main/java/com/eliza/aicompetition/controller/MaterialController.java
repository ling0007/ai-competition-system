package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.material.MaterialReviewRequest;
import com.eliza.aicompetition.dto.material.MaterialReviewResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 教师审核材料 —— 通过或留下修改意见
     */
    @PostMapping("/review")
    public ApiResponse<MaterialReviewResponse> reviewMaterial(@Valid @RequestBody MaterialReviewRequest request) {
        MaterialReviewResponse response = materialService.reviewMaterial(request);
        String msg = "approved".equals(response.reviewStatus()) ? "材料审核通过" : "已提交修改意见";
        return ApiResponse.success(msg, response);
    }

    /**
     * 重置材料审核状态 —— 清空审核结果和意见，恢复为未审核。
     */
    @PostMapping("/{materialId}/reset-review")
    public ApiResponse<Void> resetReview(@PathVariable Long materialId) {
        materialService.resetMaterialReview(materialId);
        return ApiResponse.success("审核状态已重置", null);
    }
}
