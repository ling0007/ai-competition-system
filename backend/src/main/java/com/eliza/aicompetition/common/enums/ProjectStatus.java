package com.eliza.aicompetition.common.enums;

import com.eliza.aicompetition.exception.InvalidStatusTransitionException;

import java.util.Map;
import java.util.Set;

/**
 * <h1>项目状态枚举</h1>
 *
 * <h2>状态流转图（重构后 —— 纯项目生命周期）</h2>
 * <pre>
 * DRAFT ──▶ UNDER_REVIEW ──▶ APPROVED
 *             ▲    │
 *             │    ▼
 *             └── REVISION_REQUIRED
 *
 * DRAFT → UNDER_REVIEW         // 学生提交审核
 * UNDER_REVIEW → APPROVED      // 教师审核通过
 * UNDER_REVIEW → REVISION_REQUIRED  // 教师退回修改
 * REVISION_REQUIRED → UNDER_REVIEW  // 学生修改后重新提交
 * </pre>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>项目状态只表达项目生命周期，不混入材料完整度、AI 检查结果、教师审核结果</li>
 *   <li>材料完整度通过当前有效版本实时计算；完成率仅作为缓存写回项目表</li>
 *   <li>AI 检查结果独立存储到 project_ai_check 表</li>
 *   <li>教师审核结果独立存储到 material_review 表</li>
 *   <li>没有 SUBMITTED 状态 —— DRAFT/REVISION_REQUIRED 提交后直接进入 UNDER_REVIEW</li>
 *   <li>REVISION_REQUIRED 不退回 DRAFT —— 明确区分普通草稿和曾被审核退回的项目</li>
 * </ul>
 *
 * <h2>兼容旧 DB 值（迁移期间）</h2>
 * <p>旧存储过程和旧数据可能使用 draft/incomplete/ready 等值，
 * 通过 {@link #fromDbValue(String)} 兼容映射。</p>
 */
public enum ProjectStatus {

    DRAFT("草稿"),
    UNDER_REVIEW("人工审核中"),
    REVISION_REQUIRED("退回修改"),
    APPROVED("审核通过");

    private final String label;

    /** 兼容旧 DB 存储过程中的 draft / incomplete / ready 等值 */
    private static final Map<String, ProjectStatus> DB_VALUE_MAP = Map.ofEntries(
        Map.entry("draft", DRAFT),
        Map.entry("incomplete", DRAFT),           // 旧状态 → 映射为 DRAFT
        Map.entry("ready", DRAFT),                // 旧状态 → 映射为 DRAFT
        Map.entry("ai_warning", DRAFT),           // 旧状态 → 映射为 DRAFT
        Map.entry("ai_passed", DRAFT),            // 旧状态 → 映射为 DRAFT
        Map.entry("material_incomplete", DRAFT),  // 旧状态 → 映射为 DRAFT
        Map.entry("ready_for_ai_check", DRAFT),   // 旧状态 → 映射为 DRAFT
        Map.entry("under_review", UNDER_REVIEW),
        Map.entry("revision_required", REVISION_REQUIRED),
        Map.entry("approved", APPROVED)
    );

    /** 合法流转映射：每个状态能变到哪些目标状态 */
    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS = Map.of(
        DRAFT,              Set.of(UNDER_REVIEW),
        UNDER_REVIEW,       Set.of(APPROVED, REVISION_REQUIRED),
        REVISION_REQUIRED,  Set.of(UNDER_REVIEW)
        // APPROVED 是终态，没有允许流转的目标
    );

    ProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 检查是否可以从当前状态流转到目标状态。
     * 相同状态视为幂等操作。
     */
    public boolean canTransitionTo(ProjectStatus target) {
        if (target == null) return false;
        if (this == target) return true; // 幂等
        Set<ProjectStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 校验状态流转是否合法。相同状态重复设置按幂等操作处理。
     *
     * @param target 目标状态
     * @throws InvalidStatusTransitionException 如果流转不合法
     */
    public void validateTransitionTo(ProjectStatus target) {
        if (target == null) {
            throw new InvalidStatusTransitionException("ProjectStatus", this, null);
        }
        if (this == target) return; // 幂等
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("ProjectStatus", this, target);
        }
    }

    // ==================== 领域方法 ====================

    /** 提交审核 —— 仅 DRAFT → UNDER_REVIEW。 */
    public ProjectStatus submitForReview() {
        if (this != DRAFT) {
            throw new InvalidStatusTransitionException("ProjectStatus.submit", this, UNDER_REVIEW);
        }
        validateTransitionTo(UNDER_REVIEW);
        return UNDER_REVIEW;
    }

    /**
     * 审核通过 —— UNDER_REVIEW → APPROVED。
     */
    public ProjectStatus approve() {
        validateTransitionTo(APPROVED);
        return APPROVED;
    }

    /**
     * 退回修改 —— UNDER_REVIEW → REVISION_REQUIRED。
     */
    public ProjectStatus requestRevision() {
        validateTransitionTo(REVISION_REQUIRED);
        return REVISION_REQUIRED;
    }

    /**
     * 重新提交 —— REVISION_REQUIRED → UNDER_REVIEW。
     */
    public ProjectStatus resubmit() {
        if (this != REVISION_REQUIRED) {
            throw new InvalidStatusTransitionException("ProjectStatus.resubmit", this, UNDER_REVIEW);
        }
        validateTransitionTo(UNDER_REVIEW);
        return UNDER_REVIEW;
    }

    // ==================== 兼容方法 ====================

    /** 将 DB 存储的值（包含旧值）转换为枚举 */
    public static ProjectStatus fromDbValue(String dbValue) {
        if (dbValue == null) return DRAFT;
        ProjectStatus status = DB_VALUE_MAP.get(dbValue.toLowerCase());
        return status != null ? status : DRAFT;
    }
}
