package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.entity.ProjectMaterial;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProjectMaterialMapper extends BaseMapper<ProjectMaterial> {

    List<ProjectMaterialView> findLatestDetailsByProjectId(@Param("projectId") Long projectId);
}
