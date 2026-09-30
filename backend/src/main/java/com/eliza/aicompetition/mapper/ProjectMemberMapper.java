package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMemberView;
import com.eliza.aicompetition.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    List<ProjectMemberView> findMemberViewsByProjectId(@Param("projectId") Long projectId);

    List<Long> findProjectIdsByUserId(@Param("userId") Long userId,
                                      @Param("memberRole") String memberRole);

    List<ProjectListView> findProjectListByUserId(@Param("userId") Long userId,
                                                  @Param("memberRole") String memberRole);

    /**
     * 分页查询用户参与的项目列表，支持关键字和状态筛选。
     * <p>
     * MyBatis-Plus 分页：第一个参数为 Page，框架自动拦截 SQL 做 COUNT 查询。
     * </p>
     */
    Page<ProjectListView> findProjectListByUserIdPage(
        Page<?> page,
        @Param("userId") Long userId,
        @Param("memberRole") String memberRole,
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("deadlineBefore") java.time.LocalDateTime deadlineBefore
    );

    @Update("UPDATE project_member SET is_deleted = 0, member_role = #{memberRole}, join_time = NOW(),"
        + " updated_at = NOW() WHERE project_id = #{projectId} AND user_id = #{userId} AND is_deleted = 1")
    int reactivateMember(@Param("projectId") Long projectId,
                         @Param("userId") Long userId,
                         @Param("memberRole") String memberRole);
}
