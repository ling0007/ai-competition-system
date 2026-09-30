package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.dto.user.UserOptionResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
public class UserService {
    private static final Set<String> OPTION_ROLES = Set.of("student", "teacher");
    private final SysUserMapper sysUserMapper;

    public UserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public List<UserOptionResponse> searchOptions(String keyword, String role) {
        if (!OPTION_ROLES.contains(role)) {
            throw new BusinessException("候选用户角色必须为 student 或 teacher");
        }
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getRole, role)
            .orderByAsc(SysUser::getRealName)
            .last("limit 20");
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysUser::getRealName, keyword)
                .or().like(SysUser::getUsername, keyword));
        }
        return sysUserMapper.selectList(query).stream()
            .map(user -> new UserOptionResponse(
                user.getUserId(),
                user.getRealName() + " · " + ("teacher".equals(role) ? "指导教师" : "学生"),
                user.getRole()
            ))
            .toList();
    }
}
