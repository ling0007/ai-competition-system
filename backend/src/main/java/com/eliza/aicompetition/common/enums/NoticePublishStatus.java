package com.eliza.aicompetition.common.enums;

import com.eliza.aicompetition.exception.InvalidStatusTransitionException;

import java.util.Map;
import java.util.Set;

/**
 * <h1>通知发布状态枚举</h1>
 *
 * <p>控制通知对学生的可见性。保持简洁的线性生命周期。</p>
 *
 * <h2>状态流转图</h2>
 * <pre>
 * DRAFT → PUBLISHED → ARCHIVED
 * </pre>
 *
 * <h2>禁止流转</h2>
 * <ul>
 *   <li>PUBLISHED → DRAFT</li>
 *   <li>ARCHIVED → PUBLISHED</li>
 *   <li>ARCHIVED → DRAFT</li>
 * </ul>
 *
 * <h2>发布前置条件</h2>
 * <ul>
 *   <li>管理员权限</li>
 *   <li>parseStatus == PARSED</li>
 *   <li>confirmedAt != null（管理员已确认结构化内容）</li>
 *   <li>材料清单已生成</li>
 *   <li>通知尚未发布</li>
 *   <li>必要时间和内容字段合法</li>
 * </ul>
 *
 * <p>如果通知需要停止新项目申报，直接 PUBLISHED → ARCHIVED，已创建的项目和材料继续保留。</p>
 *
 * @see NoticeParseStatus
 */
public enum NoticePublishStatus {

    DRAFT("未发布"),
    PUBLISHED("已发布"),
    ARCHIVED("已归档");

    private final String label;

    private static final Map<NoticePublishStatus, Set<NoticePublishStatus>> ALLOWED_TRANSITIONS = Map.of(
        DRAFT,     Set.of(PUBLISHED),
        PUBLISHED, Set.of(ARCHIVED)
    );

    NoticePublishStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 检查是否可以从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(NoticePublishStatus target) {
        if (target == null) return false;
        if (this == target) return true; // 幂等
        Set<NoticePublishStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 校验状态流转是否合法。相同状态重复设置按幂等操作处理。
     *
     * @param target 目标状态
     * @throws InvalidStatusTransitionException 如果流转不合法
     */
    public void validateTransitionTo(NoticePublishStatus target) {
        if (target == null) {
            throw new InvalidStatusTransitionException("NoticePublishStatus", this, null);
        }
        if (this == target) return; // 幂等
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("NoticePublishStatus", this, target);
        }
    }

    // ==================== 领域方法 ====================

    /**
     * 发布通知 —— DRAFT → PUBLISHED。
     */
    public NoticePublishStatus publish() {
        validateTransitionTo(PUBLISHED);
        return PUBLISHED;
    }

    /**
     * 归档通知 —— PUBLISHED → ARCHIVED。
     */
    public NoticePublishStatus archive() {
        validateTransitionTo(ARCHIVED);
        return ARCHIVED;
    }
}
