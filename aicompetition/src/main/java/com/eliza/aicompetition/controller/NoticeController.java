package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.notice.NoticeParseResponse;
import com.eliza.aicompetition.dto.notice.NoticeUploadResponse;
import com.eliza.aicompetition.service.NoticeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NoticeUploadResponse> uploadNotice(
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "organizer", required = false) String organizer,
        @RequestParam(value = "deadline", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
        @RequestParam(value = "targetGroup", required = false) String targetGroup,
        @RequestParam(value = "rawText", required = false) String rawText,
        @RequestParam(value = "createdBy", defaultValue = "1") Long createdBy
    ) {
        NoticeUploadResponse response = noticeService.uploadNotice(file, title, organizer, deadline, targetGroup, rawText, createdBy);
        return ApiResponse.success("通知上传成功", response);
    }

    @PostMapping("/parse/{noticeId}")
    public ApiResponse<NoticeParseResponse> parseNotice(@PathVariable Long noticeId) {
        return ApiResponse.success("通知解析成功", noticeService.parseNotice(noticeId));
    }
}
