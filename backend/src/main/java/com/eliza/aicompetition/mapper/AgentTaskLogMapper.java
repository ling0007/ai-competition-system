package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.entity.AgentTaskLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AgentTaskLogMapper extends BaseMapper<AgentTaskLog> {
    /** Best-effort post-terminal update; never participates in the business result transaction. */
    @Update("UPDATE agent_task_log SET observation_payload=#{payload}, error_category=#{errorCategory}, "
        + "degradation_reason=#{degradationReason} WHERE task_id=#{taskId} AND execute_status=#{status} "
        + "AND (observation_payload IS NULL OR (#{workerMeasured}=1 "
        + "AND JSON_UNQUOTE(JSON_EXTRACT(observation_payload,'$.workerMeasured'))='false')) "
        + "AND is_deleted=0")
    int updateObservation(@Param("taskId") Long taskId, @Param("status") String status,
        @Param("payload") String payload, @Param("errorCategory") String errorCategory,
        @Param("degradationReason") String degradationReason,
        @Param("workerMeasured") boolean workerMeasured);
}
