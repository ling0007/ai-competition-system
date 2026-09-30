package com.eliza.aicompetition.security;

import java.util.Collection;
import java.util.Collections;

/**
 * 当前登录用户 Principal。
 * <p>
 * 在 {@link JwtAuthenticationFilter} 中由 JWT 解析 + 数据库查询后构造，
 * 存入 {@link org.springframework.security.core.Authentication#getPrincipal()}，
 * 后续通过 {@link SecurityUtils#getCurrentUser()} 获取。
 * </p>
 * <p>
 * <b>注意</b>：此处不包含密码、手机号等敏感字段，仅保留鉴权和业务常用的最小信息。
 * </p>
 */
public class LoginUser {

    private final Long userId;
    private final String username;
    private final String realName;
    private final String role;

    /**
     * 用户拥有的 Spring Security 权限（authorities）。
     * <p>
     * 例如 ROLE_STUDENT、ROLE_TEACHER、ROLE_ADMIN。
     * Spring Security 的 hasRole("ADMIN") 实际匹配 ROLE_ADMIN，
     * hasAuthority("ROLE_ADMIN") 则精确匹配。
     * </p>
     */
    private final Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;

    public LoginUser(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
        // 角色转换为 Spring Security 权限：自动添加 ROLE_ 前缀
        this.authorities = Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    // ==================== Getters ====================

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public String getRole() {
        return role;
    }

    public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String toString() {
        return "LoginUser{userId=" + userId + ", username='" + username + "', role='" + role + "'}";
    }
}
