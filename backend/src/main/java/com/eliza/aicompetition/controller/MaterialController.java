package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.material.MaterialReviewRequest;
import com.eliza.aicompetition.dto.material.MaterialReviewResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.dto.material.MaterialVersionView;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 材料管理接口。
 * <p>
 * <b>安全注意</b>：操作人身份（uploadedBy）从 JWT 中获取，不从请求参数传递。
 * 审核人身份同理，防止前端伪造。
 * </p>
 */
@RestController
@RequestMapping("/material")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /**
     * 上传材料文件。
     * <p>
     * uploadedBy 从当前登录用户获取，不接受前端参数。
     * </p>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MaterialUploadResponse> uploadMaterial(
        @RequestParam("projectId") Long projectId,
        @RequestParam("requirementId") Long requirementId,
        @RequestParam(value = "remark", required = false) String remark,
        @RequestParam("file") MultipartFile file
    ) {
        // 从 SecurityContext 获取当前用户，不再信任前端传参
        Long uploadedBy = SecurityUtils.getCurrentUserId();
        MaterialUploadResponse response = materialService.uploadMaterial(projectId, requirementId, uploadedBy, remark, file);
        return ApiResponse.success("材料上传成功", response);
    }

    /**
     * 教师审核材料 —— 通过或留下修改意见。
     * <p>
     * 审核人身份从 JWT 中获取，不由请求体传递。
     * </p>
     */
    @PostMapping("/review")
    public ApiResponse<MaterialReviewResponse> reviewMaterial(@Valid @RequestBody MaterialReviewRequest request) {
        // 审核人从 SecurityContext 获取，不信任请求体
        Long reviewerId = SecurityUtils.getCurrentUserId();
        MaterialReviewResponse response = materialService.reviewMaterial(reviewerId, request);
        String msg = "APPROVED".equals(response.reviewStatus()) ? "材料审核通过" : "已提交修改意见";
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

    // ==================== P1-4: 材料列表分页查询 ====================

    /**
     * 分页查询材料列表，支持按项目和提交状态筛选。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<ProjectMaterialView>> listMaterials(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Boolean submitted,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(
            materialService.listMaterials(projectId, submitted, pageNum, pageSize));
    }

    /** 查询某项材料的全部历史版本；历史记录仅用于追溯。 */
    @GetMapping("/projects/{projectId}/requirements/{requirementId}/versions")
    public ApiResponse<List<MaterialVersionView>> getVersionHistory(
        @PathVariable Long projectId,
        @PathVariable Long requirementId
    ) {
        return ApiResponse.success(materialService.getVersionHistory(projectId, requirementId));
    }
}
