package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eliza.aicompetition.dto.notify.NotifyMessageResponse;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotifyService {

    private final NotifyMessageMapper notifyMessageMapper;

    public NotifyService(NotifyMessageMapper notifyMessageMapper) {
        this.notifyMessageMapper = notifyMessageMapper;
    }

    public List<NotifyMessageResponse> listMessages(Long receiverId, Integer isRead) {
        LambdaQueryWrapper<NotifyMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotifyMessage::getReceiverId, receiverId);
        if (isRead != null) {
            queryWrapper.eq(NotifyMessage::getIsRead, isRead);
        }
        queryWrapper.orderByDesc(NotifyMessage::getCreatedAt);

        List<NotifyMessage> messages = notifyMessageMapper.selectList(queryWrapper);
        return messages.stream()
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
    }

    public long getUnreadCount(Long receiverId) {
        LambdaQueryWrapper<NotifyMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotifyMessage::getReceiverId, receiverId)
            .eq(NotifyMessage::getIsRead, 0);
        return notifyMessageMapper.selectCount(queryWrapper);
    }

    public void markRead(Long msgId) {
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
