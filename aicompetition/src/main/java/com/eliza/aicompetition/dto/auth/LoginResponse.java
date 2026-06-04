package com.eliza.aicompetition.dto.auth;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String realName,
        String role) {
}
