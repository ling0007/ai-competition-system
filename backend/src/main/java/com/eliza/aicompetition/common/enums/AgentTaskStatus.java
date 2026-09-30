package com.eliza.aicompetition.common.enums;

import com.eliza.aicompetition.exception.InvalidStatusTransitionException;

import java.util.Map;
import java.util.Set;

/**
 * <h1>Agent 任务执行状态枚举</h1>
 *
 * <p>追踪 AI 异步任务的技术执行生命周期。注意：这是<b>技术任务状态</b>，
 * 与业务处理结果（如通知解析是否成功、AI 检查是否通过）解耦。</p>
 *
 * <h2>状态流转图</h2>
 * <pre>
 * PENDING → RUNNING → SUCCESS
 *                   → FAILED
 *                   → TIMEOUT
 * </pre>
 *
 * <h2>重要原则</h2>
 * <ul>
 *   <li>SUCCESS / FAILED / TIMEOUT 均为单次任务终态</li>
 *   <li>AI 任务重试时创建新的任务记录（parentTaskId 指向旧任务），不重置旧任务状态</li>
 *   <li>FAILED → PENDING、TIMEOUT → PENDING 等"重试"流转不存在于此枚举中</li>
 *   <li>必须明确区分 AgentTaskStatus.FAILED（一次 AI 调用失败）和 NoticeParseStatus.FAILED（通知解析业务最终失败）</li>
 * </ul>
 *
 * <h2>与业务状态的关系</h2>
 * <ul>
 *   <li>未检查 → 没有 AI 检查记录</li>
 *   <li>检查中 → AgentTaskStatus.RUNNING</li>
 *   <li>调用失败 → AgentTaskStatus.FAILED</li>
 *   <li>调用超时 → AgentTaskStatus.TIMEOUT</li>
 *   <li>存在有效结果 → AiCheckResult.PASSED / WARNING</li>
 * </ul>
 */
public enum AgentTaskStatus {

    PENDING("等待执行"),
    RUNNING("执行中"),
    SUCCESS("执行成功"),
    FAILED("执行失败"),
    TIMEOUT("执行超时");

    private final String label;

    private static final Map<AgentTaskStatus, Set<AgentTaskStatus>> ALLOWED_TRANSITIONS = Map.of(
        PENDING, Set.of(RUNNING, TIMEOUT),
        RUNNING, Set.of(SUCCESS, FAILED, TIMEOUT)
        // SUCCESS / FAILED / TIMEOUT 为终态，无允许流转目标
    );

    AgentTaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 检查是否可以从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(AgentTaskStatus target) {
        if (target == null) return false;
        if (this == target) return true; // 幂等
        Set<AgentTaskStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 校验状态流转是否合法。相同状态重复设置按幂等操作处理。
     *
     * @param target 目标状态
     * @throws InvalidStatusTransitionException 如果流转不合法
     */
    public void validateTransitionTo(AgentTaskStatus target) {
        if (target == null) {
            throw new InvalidStatusTransitionException("AgentTaskStatus", this, null);
        }
        if (this == target) return; // 幂等
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("AgentTaskStatus", this, target);
        }
    }

    // ==================== 领域方法 ====================

    /**
     * 开始执行 —— PENDING → RUNNING。
     */
    public AgentTaskStatus startRunning() {
        validateTransitionTo(RUNNING);
        return RUNNING;
    }

    /**
     * 标记成功 —— RUNNING → SUCCESS。
     */
    public AgentTaskStatus markSuccess() {
        validateTransitionTo(SUCCESS);
        return SUCCESS;
    }

    /**
     * 标记失败 —— RUNNING → FAILED。
     */
    public AgentTaskStatus markFailed() {
        validateTransitionTo(FAILED);
        return FAILED;
    }

    /**
     * 标记超时 —— RUNNING → TIMEOUT。
     */
    public AgentTaskStatus markTimeout() {
        validateTransitionTo(TIMEOUT);
        return TIMEOUT;
    }

    /**
     * 判断当前状态是否为终态。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT;
    }
}
