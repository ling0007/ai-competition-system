package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMemberView;
import com.eliza.aicompetition.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    List<ProjectMemberView> findMemberViewsByProjectId(@Param("projectId") Long projectId);

    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

    List<ProjectListView> findProjectListByUserId(@Param("userId") Long userId);

    @Update("UPDATE project_member SET is_deleted = 0, member_role = #{memberRole}, join_time = NOW(),"
        + " updated_at = NOW() WHERE project_id = #{projectId} AND user_id = #{userId} AND is_deleted = 1")
    int reactivateMember(@Param("projectId") Long projectId,
                         @Param("userId") Long userId,
                         @Param("memberRole") String memberRole);
}
