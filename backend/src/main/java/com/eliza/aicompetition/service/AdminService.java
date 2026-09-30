package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.user.UserListItemResponse;
import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理员业务 Service。
 * <p>
 * P1-4 新增：将用户列表等管理功能从 Controller 中提取到 Service 层，
 * 遵循 CLAUDE.md 规定的分层规范。
 * </p>
 */
@Service
public class AdminService {

    private final SysUserMapper sysUserMapper;

    public AdminService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 分页查询用户列表，支持关键字搜索和角色筛选。
     *
     * @param keyword  用户名或真实姓名模糊搜索
     * @param role     角色筛选（student/teacher/admin），为空则查全部
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<UserListItemResponse> listUsers(String keyword, String role, int pageNum, int pageSize) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<SysUser>()
            .orderByAsc(SysUser::getUserId);

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w
                .like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getRealName, keyword));
        }

        if (StringUtils.hasText(role)) {
            queryWrapper.eq(SysUser::getRole, role);
        }

        Page<SysUser> page = new Page<>(pageNum, pageSize);
        Page<SysUser> resultPage = sysUserMapper.selectPage(page, queryWrapper);

        java.util.List<UserListItemResponse> list = resultPage.getRecords().stream()
            .map(UserListItemResponse::from)
            .toList();

        return PageResult.of(resultPage, list);
    }
}
