package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.material.MaterialVersionView;
import com.eliza.aicompetition.entity.ProjectMaterial;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProjectMaterialMapper extends BaseMapper<ProjectMaterial> {

    List<ProjectMaterialView> findLatestDetailsByProjectId(@Param("projectId") Long projectId);

    List<MaterialVersionView> findVersionHistory(
        @Param("projectId") Long projectId,
        @Param("requirementId") Long requirementId
    );

    /**
     * P1-4: 分页查询材料列表，支持按项目和提交状态筛选。
     */
    Page<ProjectMaterialView> findMaterialsPage(
        Page<?> page,
        @Param("projectId") Long projectId,
        @Param("submitted") Boolean submitted
    );
}
