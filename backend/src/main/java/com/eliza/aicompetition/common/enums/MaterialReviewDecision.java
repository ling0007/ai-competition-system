package com.eliza.aicompetition.common.enums;

/**
 * <h1>材料审核决定枚举（不可变）</h1>
 *
 * <p>教师/管理员对材料版本的审核决定。这是一个<b>不可变结果</b>，不是状态机。</p>
 * <p>每次审核创建一条新的 {@code material_review} 记录，不覆盖旧数据。</p>
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>{@link #APPROVED} — 材料审核通过</li>
 *   <li>{@link #REVISION_REQUIRED} — 材料需要修改后重新提交</li>
 * </ul>
 *
 * <h2>重要原则</h2>
 * <ul>
 *   <li>没有审核记录 = 未审核（不需要额外的 NOT_REVIEWED 状态）</li>
 *   <li>学生上传新版本后，旧版本审核记录保留，新版本自然处于未审核状态</li>
 *   <li>不允许修改或覆盖旧审核记录</li>
 * </ul>
 *
 * @see AiCheckResult
 */
public enum MaterialReviewDecision {

    APPROVED("审核通过"),
    REVISION_REQUIRED("需修改后重新提交");

    private final String label;

    MaterialReviewDecision(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 从请求参数中的审核结果字符串映射为枚举。
     *
     * @param value 审核结果字符串（如 "approved", "revision"）
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果值无法识别
     */
    public static MaterialReviewDecision fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("审核决定不能为空");
        }
        return switch (value.toLowerCase()) {
            case "approved" -> APPROVED;
            case "revision", "revision_required" -> REVISION_REQUIRED;
            default -> throw new IllegalArgumentException("未知的审核决定: " + value);
        };
    }
}
