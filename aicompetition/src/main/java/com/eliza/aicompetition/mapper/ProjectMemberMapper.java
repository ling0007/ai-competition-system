package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.dto.project.ProjectListView;
import com.eliza.aicompetition.dto.project.ProjectMemberView;
import com.eliza.aicompetition.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    List<ProjectMemberView> findMemberViewsByProjectId(@Param("projectId") Long projectId);

    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

    List<ProjectListView> findProjectListByUserId(@Param("userId") Long userId);
}
