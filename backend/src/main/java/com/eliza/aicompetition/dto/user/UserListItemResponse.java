package com.eliza.aicompetition.dto.user;

import com.eliza.aicompetition.entity.SysUser;

import java.time.LocalDateTime;

public record UserListItemResponse(
        Long userId,
        String username,
        String realName,
        String role,
        String phone,
        LocalDateTime createdAt) {

    public static UserListItemResponse from(SysUser user) {
        return new UserListItemResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getPhone(),
                user.getCreatedAt());
    }
}
