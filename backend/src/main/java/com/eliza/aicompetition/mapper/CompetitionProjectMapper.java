package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.entity.CompetitionProject;
import org.apache.ibatis.annotations.Param;

public interface CompetitionProjectMapper extends BaseMapper<CompetitionProject> {

    /**
     * 管理员全量项目分页查询，支持关键字、状态、通知筛选。
     * <p>
     * 与 {@code ProjectMemberMapper.findProjectListByUserIdPage} 不同，
     * 本查询不过滤当前用户参与的项目，而是查询平台内所有项目。
     * </p>
     *
     * @param page     MyBatis-Plus 分页对象
     * @param keyword  项目名 / 负责人 / 通知标题模糊搜索
     * @param status   项目状态筛选（可选）
     * @param noticeId 所属通知 ID 筛选（可选）
     * @return 分页结果
     */
    Page<ProjectListView> findAllProjectsPage(
        Page<?> page,
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("noticeId") Long noticeId
    );
}
