package com.eliza.aicompetition.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.user.CreateUserRequest;
import com.eliza.aicompetition.dto.user.UpdateRoleRequest;
import com.eliza.aicompetition.dto.user.UserListItemResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminController(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * List all users, with optional keyword search (username or realName).
     */
    @GetMapping("/users")
    public ApiResponse<List<UserListItemResponse>> listUsers(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getUserId);

        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword));
        }

        List<UserListItemResponse> users = sysUserMapper.selectList(query).stream()
                .map(UserListItemResponse::from)
                .toList();

        return ApiResponse.success(users);
    }

    /**
     * Update a user's role.
     */
    @PutMapping("/users/{userId}/role")
    public ApiResponse<UserListItemResponse> updateRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateRoleRequest body) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setRole(body.getRole());
        sysUserMapper.updateById(user);
        log.info("User role updated: userId={}, newRole={}", userId, body.getRole());
        return ApiResponse.success("角色更新成功", UserListItemResponse.from(user));
    }

    /**
     * Admin creates a new user.
     */
    @PostMapping("/users")
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

        log.info("Admin created user: userId={}, username={}, role={}", user.getUserId(), user.getUsername(), user.getRole());
        return ApiResponse.success("用户创建成功", UserListItemResponse.from(user));
    }

    /**
     * Delete a user (soft delete).
     */
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        sysUserMapper.deleteById(userId);
        log.info("User deleted: userId={}, username={}", userId, user.getUsername());
        return ApiResponse.success("用户删除成功", null);
    }
}
