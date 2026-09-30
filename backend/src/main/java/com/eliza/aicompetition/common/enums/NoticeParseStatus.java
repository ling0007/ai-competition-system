package com.eliza.aicompetition.common.enums;

import com.eliza.aicompetition.exception.InvalidStatusTransitionException;

import java.util.Map;
import java.util.Set;

/**
 * <h1>通知 AI 解析状态枚举</h1>
 *
 * <h2>状态流转图</h2>
 * <pre>
 *                  ┌──────────────┐
 *                  │    DRAFT     │  上传通知后的初始状态
 *                  └──────┬───────┘
 *                         │ 触发 AI 解析
 *                         ▼
 *                  ┌──────────────┐
 *          ┌───────│   PARSING    │  AI 正在调用 LLM
 *          │       └──────┬───────┘
 *          │              │
 *          │         ┌────┴────┐
 *          │         ▼         ▼
 *          │  ┌──────────┐ ┌──────────┐
 *          │  │  PARSED  │ │  FAILED  │  LLM 返回错误/超时
 *          │  └────┬─────┘ └────┬─────┘
 *          │       │            │ 管理员重试 / 人工录入
 *          │       │            ▼
 *          │       │     ┌──────────┐
 *          └───────┴────▶│ PARSING  │  重新解析
 *                        └──────────┘
 *
 *          FAILED → PARSED  // 管理员人工填写，不需要伪造 AI 解析过程
 *          PARSED → PARSING // 重新解析已确认的内容
 * </pre>
 *
 * <h2>与管理员确认的关系</h2>
 * <p>
 * 通知确认不混入解析状态。使用通知实体中的 {@code confirmed_at} 和 {@code confirmed_by}
 * 字段表达管理员确认：
 * </p>
 * <ul>
 *   <li>{@code parseStatus == PARSED} — 存在有效的结构化内容</li>
 *   <li>{@code confirmedAt != null} — 管理员已确认结构化内容</li>
 * </ul>
 *
 * <p>发布通知前必须满足：parseStatus == PARSED、confirmedAt != null、材料清单已生成、必要字段完整。</p>
 *
 * @see NoticePublishStatus 发布状态（独立维度）
 */
public enum NoticeParseStatus {

    DRAFT("待解析"),
    PARSING("解析中"),
    PARSED("已解析"),
    FAILED("解析失败");

    private final String label;

    /**
     * 合法流转映射 —— 每个状态能变到哪些目标状态。
     */
    private static final Map<NoticeParseStatus, Set<NoticeParseStatus>> ALLOWED_TRANSITIONS = Map.of(
        DRAFT,   Set.of(PARSING),
        PARSING, Set.of(PARSED, FAILED),
        PARSED,  Set.of(PARSING),
        FAILED,  Set.of(PARSING, PARSED)
    );

    NoticeParseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 检查是否可以从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(NoticeParseStatus target) {
        if (target == null) return false;
        // 相同状态视为幂等操作
        if (this == target) return true;
        Set<NoticeParseStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 校验状态流转是否合法。相同状态重复设置按幂等操作处理，不抛异常。
     *
     * @param target 目标状态
     * @throws InvalidStatusTransitionException 如果流转不合法
     */
    public void validateTransitionTo(NoticeParseStatus target) {
        if (target == null) {
            throw new InvalidStatusTransitionException("NoticeParseStatus", this, null);
        }
        if (this == target) return; // 幂等
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("NoticeParseStatus", this, target);
        }
    }

    // ==================== 领域方法 ====================

    /**
     * 开始解析 —— DRAFT/FAILED/PARSED → PARSING。
     */
    public NoticeParseStatus startParsing() {
        validateTransitionTo(PARSING);
        return PARSING;
    }

    /**
     * 标记解析完成 —— PARSING → PARSED。
     */
    public NoticeParseStatus markParsed() {
        validateTransitionTo(PARSED);
        return PARSED;
    }

    /**
     * 标记解析失败 —— PARSING → FAILED。
     */
    public NoticeParseStatus markParseFailed() {
        validateTransitionTo(FAILED);
        return FAILED;
    }

    /**
     * 完成人工录入 —— FAILED → PARSED。
     * 管理员人工填写结构化通知内容，不需要伪造一次 AI 解析过程。
     */
    public NoticeParseStatus completeManualEntry() {
        validateTransitionTo(PARSED);
        return PARSED;
    }

    /**
     * 重新解析 —— PARSED → PARSING。
     */
    public NoticeParseStatus restartParsing() {
        validateTransitionTo(PARSING);
        return PARSING;
    }
}
