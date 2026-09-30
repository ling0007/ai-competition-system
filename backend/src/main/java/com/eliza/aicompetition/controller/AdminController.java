package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.user.CreateUserRequest;
import com.eliza.aicompetition.dto.user.UpdateRoleRequest;
import com.eliza.aicompetition.dto.user.UserListItemResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.service.AdminService;
import com.eliza.aicompetition.service.DashboardCacheService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final DashboardCacheService dashboardCacheService;

    public AdminController(AdminService adminService, SysUserMapper sysUserMapper,
            PasswordEncoder passwordEncoder, DashboardCacheService dashboardCacheService) {
        this.adminService = adminService;
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.dashboardCacheService = dashboardCacheService;
    }

    /**
     * 分页查询用户列表，支持关键字搜索和角色筛选。
     */
    @GetMapping("/users")
    public ApiResponse<PageResult<UserListItemResponse>> listUsers(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listUsers(keyword, role, pageNum, pageSize));
    }

    /**
     * Update a user's role.
     */
    @PutMapping("/users/{userId}/role")
    @Transactional
    public ApiResponse<UserListItemResponse> updateRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateRoleRequest body) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setRole(body.getRole());
        sysUserMapper.updateById(user);
        dashboardCacheService.invalidateGlobalAfterCommit("user_role_changed");
        log.info("User role updated: userId={}, newRole={}", userId, body.getRole());
        return ApiResponse.success("角色更新成功", UserListItemResponse.from(user));
    }

    /**
     * Admin creates a new user.
     */
    @PostMapping("/users")
    @Transactional
    public ApiResponse<UserListItemResponse> createUser(@Valid @RequestBody CreateUserRequest body) {
        long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, body.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(body.getUsername());
        user.setPassword(passwordEncoder.encode(body.getPassword()));
        user.setRealName(body.getRealName());
        user.setRole(body.getRole());
        user.setPhone(body.getPhone());
        sysUserMapper.insert(user);
        dashboardCacheService.invalidateGlobalAfterCommit("user_created");

        log.info("Admin created user: userId={}, username={}, role={}", user.getUserId(), user.getUsername(), user.getRole());
        return ApiResponse.success("用户创建成功", UserListItemResponse.from(user));
    }

    /**
     * Delete a user (soft delete).
     */
    @DeleteMapping("/users/{userId}")
    @Transactional
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        sysUserMapper.deleteById(userId);
        dashboardCacheService.invalidateGlobalAfterCommit("user_deleted");
        log.info("User deleted: userId={}, username={}", userId, user.getUsername());
        return ApiResponse.success("用户删除成功", null);
    }
}
