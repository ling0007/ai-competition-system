package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.common.enums.NoticePublishStatus;
import com.eliza.aicompetition.dto.notice.ConfirmParseRequest;
import com.eliza.aicompetition.dto.notice.NoticeParseTaskResponse;
import com.eliza.aicompetition.dto.notice.NoticeTaskView;
import com.eliza.aicompetition.dto.notice.NoticeDetailResponse;
import com.eliza.aicompetition.dto.notice.NoticeUploadResponse;
import com.eliza.aicompetition.dto.notice.ParseDraftResponse;
import com.eliza.aicompetition.dto.notice.UpdateParseDraftRequest;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 申报通知管理接口。
 * <p>
 * <b>权限说明</b>：上传通知、AI 解析、解析确认、发布/归档仅限管理员操作。
 * </p>
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;
    private final NoticeAiTaskDispatcher taskDispatcher;

    public NoticeController(NoticeService noticeService, NoticeAiTaskDispatcher taskDispatcher) {
        this.noticeService = noticeService;
        this.taskDispatcher = taskDispatcher;
    }

    /**
     * 上传申报通知。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NoticeUploadResponse> uploadNotice(
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "organizer", required = false) String organizer,
        @RequestParam(value = "deadline", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
        @RequestParam(value = "targetGroup", required = false) String targetGroup,
        @RequestParam(value = "rawText", required = false) String rawText
    ) {
        Long createdBy = SecurityUtils.getCurrentUserId();
        NoticeUploadResponse response = noticeService.uploadNotice(file, title, organizer, deadline, targetGroup, rawText, createdBy);
        return ApiResponse.success("通知上传成功", response);
    }

    /** 替换尚未生成解析草稿的通知附件。 */
    @PutMapping(value = "/{noticeId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NoticeUploadResponse> replaceAttachment(
        @PathVariable Long noticeId,
        @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success("通知附件已替换，请重新进行 AI 解析",
            noticeService.replaceAttachment(noticeId, file, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 触发 AI 解析通知（解析结果写入草稿，不直接覆盖正式数据）。
     */
    @PostMapping("/parse/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NoticeParseTaskResponse> parseNotice(@PathVariable Long noticeId) {
        NoticeParseTaskResponse accepted = noticeService.acceptParseTask(noticeId, SecurityUtils.getCurrentUserId());
        taskDispatcher.dispatch(accepted.taskId());
        return ApiResponse.success("AI解析任务已接受", accepted);
    }

    @GetMapping("/parse-tasks/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NoticeTaskView> getParseTask(@PathVariable Long taskId) {
        return ApiResponse.success(noticeService.findTask(taskId));
    }

    @GetMapping("/{noticeId}/parse-task/latest")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NoticeTaskView> getLatestParseTask(@PathVariable Long noticeId) {
        return ApiResponse.success(noticeService.latestTask(noticeId));
    }

    // ==================== P1-1: 解析草稿管理接口 ====================

    /**
     * 查看解析草稿。
     */
    @GetMapping("/{noticeId}/parse-draft")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ParseDraftResponse> getParseDraft(@PathVariable Long noticeId) {
        return ApiResponse.success(noticeService.getParseDraft(noticeId));
    }

    /**
     * 修改解析草稿（管理员手动编辑 AI 提取结果）。
     */
    @PutMapping("/{noticeId}/parse-draft")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ParseDraftResponse> updateParseDraft(
        @PathVariable Long noticeId,
        @Valid @RequestBody UpdateParseDraftRequest request
    ) {
        return ApiResponse.success("草稿已更新", noticeService.updateParseDraft(noticeId, request));
    }

    /**
     * 确认解析结果（正式写入 competition_notice 和 material_requirement）。
     */
    @PostMapping("/{noticeId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> confirmParse(
        @PathVariable Long noticeId,
        @Valid @RequestBody ConfirmParseRequest request
    ) {
        noticeService.confirmParse(noticeId, request);
        return ApiResponse.success("解析结果已确认，通知数据已更新", null);
    }

    // ==================== 通知管理接口 ====================

    @GetMapping("/list")
    public ApiResponse<PageResult<CompetitionNotice>> listNotices(
        @RequestParam(required = false, defaultValue = "") String keyword,
        @RequestParam(required = false) String publishStatus,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        if ("student".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            publishStatus = NoticePublishStatus.PUBLISHED.name();
        }
        var page = noticeService.listNotices(keyword, publishStatus, startDate, endDate, pageNum, pageSize);
        var pageResult = PageResult.of(page, page.getRecords());
        return ApiResponse.success("通知列表查询成功", pageResult);
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNoticeDetail(@PathVariable Long noticeId) {
        NoticeDetailResponse detail = noticeService.getNoticeDetail(noticeId);
        if ("student".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                && !NoticePublishStatus.PUBLISHED.name().equals(detail.publishStatus())) {
            throw new BusinessException(404, "通知不存在或尚未发布");
        }
        return ApiResponse.success(detail);
    }

    @PostMapping("/{noticeId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> publishNotice(@PathVariable Long noticeId) {
        noticeService.publishNotice(noticeId);
        return ApiResponse.success("通知已发布，学生现在可以创建申报项目", null);
    }

    @PostMapping("/{noticeId}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> archiveNotice(@PathVariable Long noticeId) {
        noticeService.archiveNotice(noticeId);
        return ApiResponse.success("通知已归档", null);
    }
}
