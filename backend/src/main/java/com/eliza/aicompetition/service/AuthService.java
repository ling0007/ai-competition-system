package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.dto.auth.LoginRequest;
import com.eliza.aicompetition.dto.auth.LoginResponse;
import com.eliza.aicompetition.dto.auth.RegisterRequest;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DashboardCacheService dashboardCacheService;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            DashboardCacheService dashboardCacheService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.dashboardCacheService = dashboardCacheService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setRole(request.getRole());
        user.setPhone(request.getPhone());
        sysUserMapper.insert(user);
        dashboardCacheService.invalidateGlobalAfterCommit("user_registered");

        log.info("新用户注册成功: username={}, role={}", user.getUsername(), user.getRole());

        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUserId(), user.getUsername(), user.getRealName(), user.getRole(), user.getPhone());
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));

        // 固定一个虚拟密文，用户不存在时用来做无意义比对，对齐耗时
        String dbPassword = (user != null) ? user.getPassword() : "$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        boolean isPwdMatch = passwordEncoder.matches(request.getPassword(), dbPassword);

        // 账号为空 OR 密码不匹配，统一报错
        if (user == null || !isPwdMatch) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUserId(), user.getUsername(), user.getRealName(), user.getRole(), user.getPhone());
    }
}
