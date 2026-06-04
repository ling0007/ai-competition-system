package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.dto.project.ReviewRecordView;
import com.eliza.aicompetition.entity.ReviewRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {

    List<ReviewRecordView> findReviewViewsByProjectId(@Param("projectId") Long projectId);
}
