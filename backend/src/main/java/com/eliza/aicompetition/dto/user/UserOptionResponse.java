package com.eliza.aicompetition.dto.user;

/** 安全的项目成员候选项，不暴露手机号等个人资料。 */
public record UserOptionResponse(Long value, String label, String role) {}
