package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.notify.NotifyMessageResponse;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.NotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息通知接口。
 * <p>
 * <b>P0-3 安全注意</b>：消息隔离 —— 用户只能查看自己的消息，
 * receiverId 由后端从 JWT 获取，不信任前端传参。
 * 管理员可以查看所有用户的消息。
 * </p>
 */
@RestController
@RequestMapping("/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /**
     * 查询当前用户的消息列表（分页 + 筛选）。
     * <p>
     * 普通用户只能查看发给自己的消息，管理员可通过 receiverId 参数查看他人消息。
     * </p>
     */
    @GetMapping("/messages")
    public ApiResponse<com.eliza.aicompetition.common.PageResult<NotifyMessageResponse>> listMessages(
        @RequestParam(required = false) Long receiverId,
        @RequestParam(required = false) Integer isRead,
        @RequestParam(required = false, defaultValue = "") String keyword,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        Long effectiveReceiverId = resolveReceiverId(receiverId);
        return ApiResponse.success("消息列表查询成功",
            notifyService.listMessages(effectiveReceiverId, isRead, keyword, pageNum, pageSize));
    }

    /**
     * 获取当前用户的未读消息数。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@RequestParam(required = false) Long receiverId) {
        Long effectiveReceiverId = resolveReceiverId(receiverId);
        return ApiResponse.success("未读消息数查询成功", notifyService.getUnreadCount(effectiveReceiverId));
    }

    /**
     * 标记单条消息为已读。
     */
    @PutMapping("/{msgId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long msgId) {
        notifyService.markRead(msgId);
        return ApiResponse.success("消息已标记为已读", null);
    }

    /**
     * 标记当前用户的所有消息为已读。
     */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestParam(required = false) Long receiverId) {
        Long effectiveReceiverId = resolveReceiverId(receiverId);
        notifyService.markAllRead(effectiveReceiverId);
        return ApiResponse.success("全部消息已标记为已读", null);
    }

    /**
     * 解析消息接收人 ID。
     * <p>
     * <b>安全规则</b>：
     * <ul>
     *   <li>普通用户：忽略前端传参，使用当前登录用户 ID</li>
     *   <li>管理员：如果传了 receiverId，使用传入值（允许管理员查看他人消息）；
     *       否则使用当前用户 ID</li>
     * </ul>
     * 这种设计防止普通用户通过修改 receiverId 参数查看他人消息（水平越权）。
     * </p>
     */
    private Long resolveReceiverId(Long requestedReceiverId) {
        String currentRole = SecurityUtils.getCurrentUserRole();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 管理员可以通过传参查看指定用户的消息
        if ("admin".equalsIgnoreCase(currentRole) && requestedReceiverId != null) {
            return requestedReceiverId;
        }
        // 普通用户：忽略传参，强制只看自己的消息
        return currentUserId;
    }
}
