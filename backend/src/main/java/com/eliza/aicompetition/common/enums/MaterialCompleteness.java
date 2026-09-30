package com.eliza.aicompetition.common.enums;

/**
 * <h1>材料完整度枚举（派生属性，不是状态机）</h1>
 *
 * <p>由材料清单和当前有效版本<b>实时计算</b>，不作为项目核心状态落库。</p>
 *
 * <h2>计算规则</h2>
 * <pre>
 * requiredMaterials.stream()
 *     .allMatch(m -> m.getCurrentVersionId() != null)
 *     ? COMPLETE : INCOMPLETE
 * </pre>
 *
 * <h2>重要原则</h2>
 * <ul>
 *   <li>不需要 {@code ALLOWED_TRANSITIONS}</li>
 *   <li>不需要 {@code validateTransition()}</li>
 *   <li>原则上不作为项目核心状态落库</li>
 *   <li>如果为了列表性能进行缓存，必须明确缓存刷新机制，以材料版本数据为真实来源</li>
 * </ul>
 *
 * <p>此枚举仅用于 DTO 或展示层，不参与状态机逻辑。</p>
 */
public enum MaterialCompleteness {

    INCOMPLETE("材料未齐"),
    COMPLETE("材料已齐");

    private final String label;

    MaterialCompleteness(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
