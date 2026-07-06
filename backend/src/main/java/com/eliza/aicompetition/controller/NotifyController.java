package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.notify.NotifyMessageResponse;
import com.eliza.aicompetition.service.NotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<NotifyMessageResponse>> listMessages(
        @RequestParam Long receiverId,
        @RequestParam(required = false) Integer isRead
    ) {
        return ApiResponse.success("消息列表查询成功", notifyService.listMessages(receiverId, isRead));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@RequestParam Long receiverId) {
        return ApiResponse.success("未读消息数查询成功", notifyService.getUnreadCount(receiverId));
    }

    @PutMapping("/{msgId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long msgId) {
        notifyService.markRead(msgId);
        return ApiResponse.success("消息已标记为已读", null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestParam Long receiverId) {
        notifyService.markAllRead(receiverId);
        return ApiResponse.success("全部消息已标记为已读", null);
    }
}
