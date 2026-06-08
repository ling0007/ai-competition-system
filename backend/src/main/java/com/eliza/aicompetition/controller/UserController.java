package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.user.ChangePasswordRequest;
import com.eliza.aicompetition.dto.user.UpdateProfileRequest;
import com.eliza.aicompetition.dto.user.UserProfileResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserController(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Get current user's profile.
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(HttpServletRequest request) {
        SysUser user = resolveCurrentUser(request);
        return ApiResponse.success(UserProfileResponse.from(user));
    }

    /**
     * Update current user's profile (username, realName, phone).
     */
    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest body,
            HttpServletRequest request) {
        SysUser user = resolveCurrentUser(request);

        // Check if username is taken by another user
        if (!user.getUsername().equals(body.getUsername())) {
            long count = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, body.getUsername())
                            .ne(SysUser::getUserId, user.getUserId()));
            if (count > 0) {
                throw new BusinessException("用户名已被其他用户使用");
            }
        }

        user.setUsername(body.getUsername());
        user.setRealName(body.getRealName());
        user.setPhone(body.getPhone());
        sysUserMapper.updateById(user);
        log.info("User profile updated: userId={}, username={}", user.getUserId(), user.getUsername());
        return ApiResponse.success("个人信息更新成功", UserProfileResponse.from(user));
    }

    /**
     * Change current user's password.
     */
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        SysUser user = resolveCurrentUser(request);

        if (!passwordEncoder.matches(body.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(body.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("Password changed: userId={}", user.getUserId());
        return ApiResponse.success("密码修改成功，请重新登录", null);
    }

    private SysUser resolveCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("未登录或Token无效");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(token)) {
            throw new BusinessException("Token无效或已过期");
        }

        Long userId = jwtUtil.getUserId(token);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
