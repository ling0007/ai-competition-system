package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.user.ChangePasswordRequest;
import com.eliza.aicompetition.dto.user.UpdateProfileRequest;
import com.eliza.aicompetition.dto.user.UserProfileResponse;
import com.eliza.aicompetition.dto.user.UserOptionResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import com.eliza.aicompetition.service.UserService;
import com.eliza.aicompetition.service.DashboardCacheService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 当前用户个人信息管理接口。
 * <p>
 * <b>安全注意</b>：用户身份通过 {@link SecurityUtils} 从 Spring Security 的
 * SecurityContext 中获取，不再手动解析 HttpServletRequest 中的 JWT。
 * 这确保了一次数据库查询即可完成用户身份识别，无需重复解析 token。
 * </p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final DashboardCacheService dashboardCacheService;

    public UserController(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder,
            UserService userService, DashboardCacheService dashboardCacheService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.dashboardCacheService = dashboardCacheService;
    }

    /**
     * 获取当前用户的个人信息。
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        SysUser user = resolveCurrentUser();
        return ApiResponse.success(UserProfileResponse.from(user));
    }

    @GetMapping("/options")
    public ApiResponse<List<UserOptionResponse>> searchOptions(
        @RequestParam(required = false, defaultValue = "") String keyword,
        @RequestParam String role
    ) {
        return ApiResponse.success(userService.searchOptions(keyword, role));
    }

    /**
     * 更新当前用户的个人信息（用户名、真实姓名、手机号）。
     */
    @PutMapping("/profile")
    @Transactional
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
        SysUser user = resolveCurrentUser();

        // 检查新用户名是否已被其他用户使用
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
        dashboardCacheService.invalidateGlobalAfterCommit("user_profile_changed");
        log.info("用户信息已更新: userId={}, username={}", user.getUserId(), user.getUsername());
        return ApiResponse.success("个人信息更新成功", UserProfileResponse.from(user));
    }

    /**
     * 修改当前用户的密码。
     */
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        SysUser user = resolveCurrentUser();

        if (!passwordEncoder.matches(body.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(body.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("密码已修改: userId={}", user.getUserId());
        return ApiResponse.success("密码修改成功，请重新登录", null);
    }

    /**
     * 从 Spring Security 的 SecurityContext 中获取当前登录用户。
     * <p>
     * 相比之前的手动 JWT 解析方案，此方法：
     * <ul>
     *   <li>零数据库查询 — 用户身份已在 JwtAuthenticationFilter 中验证</li>
     *   <li>代码统一 — 全局通过 SecurityUtils 获取用户，不再分散到各 Controller</li>
     *   <li>防止伪造 — 用户身份来自 JWT，不从请求参数获取</li>
     * </ul>
     * </p>
     *
     * @return 当前登录的 SysUser 实体（含完整数据库记录，用于更新操作）
     */
    private SysUser resolveCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在或已被删除");
        }
        return user;
    }
}
