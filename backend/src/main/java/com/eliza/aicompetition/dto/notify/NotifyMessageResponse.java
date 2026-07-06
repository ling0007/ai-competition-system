package com.eliza.aicompetition.dto.notify;

import java.time.LocalDateTime;

public record NotifyMessageResponse(
    Long msgId,
    Long projectId,
    Long receiverId,
    String msgType,
    String msgContent,
    Integer isRead,
    LocalDateTime createdAt
) {
}
