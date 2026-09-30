package com.eliza.aicompetition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.eliza.aicompetition.common.enums.AgentTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task_log")
public class AgentTaskLog {
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;
    private Long projectId;
    private String toolName;

    /** 业务类型：NOTICE_PARSE-通知解析, MATERIAL_CHECK-材料检查 */
    private String businessType;

    /** 关联业务ID（如notice_id, project_id） */
    private Long businessId;

    /** 父任务ID：重试时指向原始任务 */
    private Long parentTaskId;

    /** 尝试次数：首次=1，每次重试递增 */
    private Integer attemptNo;

    private String inputSummary;

    /** 请求负载（JSON） */
    private String requestPayload;

    private String resultSummary;

    /** 响应负载（JSON） */
    private String responsePayload;

    /** Safe, optional execution metadata; never includes source text or prompt bodies. */
    private String observationPayload;

    /** 错误信息 */
    private String errorMessage;
    private String errorCategory;
    private String degradationReason;

    /**
     * 执行状态（枚举 {@link AgentTaskStatus}）：
     * PENDING-等待执行, RUNNING-执行中, SUCCESS-执行成功, FAILED-执行失败, TIMEOUT-执行超时。
     */
    private String executeStatus;
    /** MODEL / FALLBACK / NONE; pre-migration rows remain UNKNOWN. */
    private String resultOrigin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer activeMarker;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    // ==================== 领域方法 ====================

    /**
     * 开始执行 —— PENDING → RUNNING。
     */
    public void startRunning() {
        AgentTaskStatus current = this.executeStatus != null
            ? AgentTaskStatus.valueOf(this.executeStatus)
            : AgentTaskStatus.PENDING;
        this.executeStatus = current.startRunning().name();
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 标记成功 —— RUNNING → SUCCESS。
     */
    public void markSuccess() {
        AgentTaskStatus current = AgentTaskStatus.valueOf(this.executeStatus);
        this.executeStatus = current.markSuccess().name();
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 标记失败 —— RUNNING → FAILED。
     */
    public void markFailed(String errorMessage) {
        AgentTaskStatus current = AgentTaskStatus.valueOf(this.executeStatus);
        this.executeStatus = current.markFailed().name();
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 标记超时 —— PENDING/RUNNING → TIMEOUT。
     */
    public void markTimeout() {
        AgentTaskStatus current = AgentTaskStatus.valueOf(this.executeStatus);
        this.executeStatus = current.markTimeout().name();
        this.finishedAt = LocalDateTime.now();
    }
}
