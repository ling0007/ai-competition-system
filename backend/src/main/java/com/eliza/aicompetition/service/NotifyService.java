package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eliza.aicompetition.dto.notify.NotifyMessageResponse;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotifyService {

    private final NotifyMessageMapper notifyMessageMapper;

    public NotifyService(NotifyMessageMapper notifyMessageMapper) {
        this.notifyMessageMapper = notifyMessageMapper;
    }

    public com.eliza.aicompetition.common.PageResult<NotifyMessageResponse> listMessages(
        Long receiverId, Integer isRead, String keyword, int pageNum, int pageSize
    ) {
        LambdaQueryWrapper<NotifyMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotifyMessage::getReceiverId, receiverId);
        if (isRead != null) {
            queryWrapper.eq(NotifyMessage::getIsRead, isRead);
        }
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            queryWrapper.like(NotifyMessage::getMsgContent, keyword);
        }
        queryWrapper.orderByDesc(NotifyMessage::getCreatedAt);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<NotifyMessage> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<NotifyMessage> resultPage =
            notifyMessageMapper.selectPage(page, queryWrapper);

        var list = resultPage.getRecords().stream()
            .map(msg -> new NotifyMessageResponse(
                msg.getMsgId(),
                msg.getProjectId(),
                msg.getReceiverId(),
                msg.getMsgType(),
                msg.getMsgContent(),
                msg.getIsRead(),
                msg.getCreatedAt()
            ))
            .collect(Collectors.toList());

        return com.eliza.aicompetition.common.PageResult.of(resultPage, list);
    }

    public long getUnreadCount(Long receiverId) {
        LambdaQueryWrapper<NotifyMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotifyMessage::getReceiverId, receiverId)
            .eq(NotifyMessage::getIsRead, 0);
        return notifyMessageMapper.selectCount(queryWrapper);
    }

    public void markRead(Long msgId) {
        NotifyMessage existing = notifyMessageMapper.selectById(msgId);
        if (existing == null) {
            throw new BusinessException(404, "消息不存在: msgId=" + msgId);
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                && !currentUserId.equals(existing.getReceiverId())) {
            throw new BusinessException(403, "无权修改该消息");
        }
        NotifyMessage message = new NotifyMessage();
        message.setMsgId(msgId);
        message.setIsRead(1);
        notifyMessageMapper.updateById(message);
    }

    public void markAllRead(Long receiverId) {
        LambdaUpdateWrapper<NotifyMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(NotifyMessage::getReceiverId, receiverId)
            .eq(NotifyMessage::getIsRead, 0)
            .set(NotifyMessage::getIsRead, 1);
        notifyMessageMapper.update(updateWrapper);
    }
}
